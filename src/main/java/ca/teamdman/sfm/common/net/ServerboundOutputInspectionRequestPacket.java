package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour.Branch;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMUtils;
import ca.teamdman.sfml.ast.Number;
import ca.teamdman.sfml.ast.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record ServerboundOutputInspectionRequestPacket(
        String programString,
        int outputNodeIndex
) {
    public static void encode(
            ServerboundOutputInspectionRequestPacket msg,
            FriendlyByteBuf friendlyByteBuf
    ) {
        friendlyByteBuf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
        friendlyByteBuf.writeInt(msg.outputNodeIndex());
    }

    public static ServerboundOutputInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {
        return new ServerboundOutputInspectionRequestPacket(
                friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
                friendlyByteBuf.readInt()
        );
    }

    public static void handle(
            ServerboundOutputInspectionRequestPacket msg,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        contextSupplier.get().enqueueWork(() -> {
            // todo: duplicate code
            // we don't know if the player has the program edit screen open from a manager or a disk in hand
            ServerPlayer player = contextSupplier.get().getSender();
            if (player == null) return;
            ManagerBlockEntity manager;
            if (player.containerMenu instanceof ManagerContainerMenu mcm) {
                if (player.getLevel().getBlockEntity(mcm.MANAGER_POSITION) instanceof ManagerBlockEntity mbe) {
                    manager = mbe;
                } else {
                    return;
                }
            } else {
                //todo: localize
                SFMPackets.INSPECTION_CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new ClientboundInputInspectionResultsPacket(
                                "This inspection is only available when editing inside a manager.")
                );
                return;
            }
            Program.compile(
                    msg.programString,
                    successProgram -> successProgram.builder()
                            .getNodeAtIndex(msg.outputNodeIndex)
                            .filter(OutputStatement.class::isInstance)
                            .map(OutputStatement.class::cast)
                            .ifPresent(outputStatement -> {
                                String payload = getOutputStatementInspectionResultsString(
                                        manager,
                                        successProgram,
                                        outputStatement
                                );
                                SFM.LOGGER.debug(
                                        "Sending output inspection results packet with length {}",
                                        payload.length()
                                );
                                SFMPackets.INSPECTION_CHANNEL.send(
                                        PacketDistributor.PLAYER.with(() -> player),
                                        new ClientboundOutputInspectionResultsPacket(payload)
                                );
                            }),
                    failure -> {
                        //todo: translate
                        SFMPackets.INSPECTION_CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> player),
                                new ClientboundOutputInspectionResultsPacket("failed to compile program")
                        );
                    }
            );
        });
        contextSupplier.get().setPacketHandled(true);
    }

    public static String getOutputStatementInspectionResultsString(
            ManagerBlockEntity manager,
            Program successProgram,
            OutputStatement outputStatement
    ) {
        StringBuilder payload = new StringBuilder();
        payload.append(outputStatement.toStringPretty()).append("\n");
        payload.append("-- predictions may differ from actual execution results\n");

        AtomicInteger branchCount = new AtomicInteger(0);
        successProgram.replaceOutputStatement(outputStatement, new OutputStatement(
                outputStatement.labelAccess(),
                outputStatement.resourceLimits(),
                outputStatement.each()
        ) {
            @Override
            public void tick(ProgramContext context) {
                if (!(context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour behaviour)) {
                    throw new IllegalStateException("Expected behaviour to be SimulateExploreAllPathsProgramBehaviour");
                }
                StringBuilder branchPayload = new StringBuilder();

                payload
                        .append("-- POSSIBILITY ")
                        .append(branchCount.getAndIncrement())
                        .append(" --");
                if (behaviour.getCurrentPath().streamBranches().allMatch(Branch::wasTrue)) {
                    payload.append(" all true\n");
                } else if (behaviour
                        .getCurrentPath()
                        .streamBranches()
                        .allMatch(Predicate.not(Branch::wasTrue))) {
                    payload.append(" all false\n");
                } else {
                    payload.append('\n');
                }
                behaviour.getCurrentPath()
                        .streamBranches()
                        .forEach(branch -> {
                            if (branch.wasTrue()) {
                                payload
                                        .append(branch.ifStatement().condition().sourceCode())
                                        .append(" -- true");
                            } else {
                                payload
                                        .append(branch.ifStatement().condition().sourceCode())
                                        .append(" -- false");
                            }
                            payload.append("\n");
                        });
                payload.append("\n");


                branchPayload.append("-- predicted inputs:\n");
                List<Pair<LimitedInputSlot<?, ?, ?>, LabelAccess>> inputSlots = new ArrayList<>();
                context
                        .getInputs()
                        .forEach(inputStatement -> inputStatement.gatherSlots(
                                context,
                                slot -> inputSlots.add(new Pair<>(
                                        slot,
                                        inputStatement.labelAccess()
                                ))
                        ));
                List<InputStatement> inputStatements = inputSlots.stream()
                        .map(slot -> SFMUtils.getInputStatementForSlot(slot.a, slot.b))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();
                if (inputStatements.isEmpty()) {
                    branchPayload.append("none\n-- predicted outputs:\nnone");
                } else {
                    inputStatements.stream()
                            .map(InputStatement::toStringPretty)
                            .map(x -> x + "\n")
                            .forEach(branchPayload::append);

                    branchPayload.append(
                            "-- predicted outputs:\n");
                    ResourceLimits condensedResourceLimits;
                    {
                        ResourceLimits resourceLimits = new ResourceLimits(
                                inputSlots
                                        .stream()
                                        .map(slot -> slot.a)
                                        .map(ServerboundOutputInspectionRequestPacket::getSlotResource)
                                        .toList(),
                                ResourceIdSet.EMPTY
                        );
                        List<ResourceLimit<?, ?, ?>> condensedResourceLimitList = new ArrayList<>();
                        for (ResourceLimit<?, ?, ?> resourceLimit : resourceLimits.resourceLimits()) {
                            // check if an existing resource limit has the same resource identifier
                            condensedResourceLimitList
                                    .stream()
                                    .filter(x -> x
                                            .resourceId()
                                            .equals(resourceLimit.resourceId()))
                                    .findFirst()
                                    .ifPresentOrElse(found -> {
                                        int i = condensedResourceLimitList.indexOf(found);
                                        ResourceLimit<?, ?, ?> newLimit = found.withLimit(new Limit(
                                                found
                                                        .limit()
                                                        .quantity()
                                                        .add(resourceLimit.limit().quantity()),
                                                ResourceQuantity.MAX_QUANTITY
                                        ));
                                        condensedResourceLimitList.set(i, newLimit);
                                    }, () -> condensedResourceLimitList.add(resourceLimit));
                        }
                        {
                            // prune items not covered by the output resource limits
                            ListIterator<ResourceLimit<?, ?, ?>> iter = condensedResourceLimitList.listIterator();
                            while (iter.hasNext()) {
                                ResourceLimit<?, ?, ?> resourceLimit = iter.next();
                                // because these resource limits were generated from resource stacks
                                // they should always be valid resource locations (not patterns)
                                ResourceLocation resourceLimitLocation = new ResourceLocation(
                                        resourceLimit.resourceId().resourceNamespace,
                                        resourceLimit.resourceId().resourceName
                                );
                                long accept = outputStatement
                                        .resourceLimits()
                                        .resourceLimits()
                                        .stream()
                                        .filter(outputResourceLimit -> outputResourceLimit
                                                                               .resourceId()
                                                                               .matchesStack(
                                                                                       resourceLimitLocation)
                                                                       && outputStatement
                                                                               .resourceLimits()
                                                                               .exclusions()
                                                                               .resourceIds()
                                                                               .stream()
                                                                               .noneMatch(
                                                                                       exclusion -> exclusion.matchesStack(
                                                                                               resourceLimitLocation)))
                                        .mapToLong(rl -> rl.limit().quantity().number().value())
                                        .max()
                                        .orElse(0);
                                if (accept == 0) {
                                    iter.remove();
                                } else {
                                    iter.set(resourceLimit.withLimit(new Limit(
                                            new ResourceQuantity(new Number(Long.min(
                                                    accept,
                                                    resourceLimit
                                                            .limit()
                                                            .quantity()
                                                            .number()
                                                            .value()
                                            )), resourceLimit.limit().quantity()
                                                                         .idExpansionBehaviour()),
                                            ResourceQuantity.MAX_QUANTITY
                                    )));
                                }
                            }
                        }
                        condensedResourceLimits = new ResourceLimits(
                                condensedResourceLimitList,
                                ResourceIdSet.EMPTY
                        );
                    }
                    if (condensedResourceLimits.resourceLimits().isEmpty()) {
                        branchPayload.append("none\n");
                    } else {
                        branchPayload
                                .append(new OutputStatement(
                                        outputStatement.labelAccess(),
                                        condensedResourceLimits,
                                        outputStatement.each()
                                ).toStringPretty());
                    }

                }
                branchPayload.append("\n");
                payload.append(branchPayload.toString().indent(4));
            }
        });

        successProgram.tick(new ProgramContext(
                successProgram,
                manager,
                new SimulateExploreAllPathsProgramBehaviour()
        ));

        return payload.toString().strip();
    }

    private static <STACK, ITEM, CAP> ResourceLimit<STACK, ITEM, CAP> getSlotResource(
            LimitedInputSlot<STACK, ITEM, CAP> limitedInputSlot
    ) {
        ResourceType<STACK, ITEM, CAP> resourceType = limitedInputSlot.type;
        //noinspection OptionalGetWithoutIsPresent
        ResourceKey<ResourceType<STACK, ITEM, CAP>> resourceTypeResourceKey = SFMResourceTypes.DEFERRED_TYPES
                .get()
                .getResourceKey(limitedInputSlot.type)
                .map(x -> {
                    //noinspection unchecked,rawtypes
                    return (ResourceKey<ResourceType<STACK, ITEM, CAP>>) (ResourceKey) x;
                })
                .get();
        STACK stack = limitedInputSlot.peekExtractPotential();
        long amount = limitedInputSlot.type.getAmount(stack);
        amount = Long.min(amount, limitedInputSlot.tracker.getResourceLimit().limit().quantity().number().value());
        long remainingObligation = limitedInputSlot.tracker.getRemainingRetentionObligation();
        amount -= Long.min(amount, remainingObligation);
        Limit amountLimit = new Limit(
                new ResourceQuantity(new Number(amount), ResourceQuantity.IdExpansionBehaviour.NO_EXPAND),
                ResourceQuantity.MAX_QUANTITY
        );
        ResourceLocation stackId = resourceType.getRegistryKey(stack);
        ResourceIdentifier<STACK, ITEM, CAP> resourceIdentifier = new ResourceIdentifier<>(
                resourceTypeResourceKey.location().getNamespace(),
                resourceTypeResourceKey.location().getPath(),
                stackId.getNamespace(),
                stackId.getPath()
        );
        return new ResourceLimit<>(
                resourceIdentifier,
                amountLimit,
                resourceIdentifier.getDefaultWith()
        );
    }
}
