/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ca.teamdman.sfm.common.flow.data.impl;

import ca.teamdman.sfm.client.gui.flow.core.FlowComponent;
import ca.teamdman.sfm.client.gui.flow.impl.manager.FlowOutputButton;
import ca.teamdman.sfm.client.gui.flow.impl.manager.core.ManagerFlowController;
import ca.teamdman.sfm.common.flow.data.core.FlowData;
import ca.teamdman.sfm.common.flow.data.core.FlowDataFactory;
import ca.teamdman.sfm.common.flow.data.core.Position;
import ca.teamdman.sfm.common.flow.data.core.PositionHolder;
import ca.teamdman.sfm.common.registrar.FlowDataFactoryRegistrar.FlowDataFactories;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

public class TileOutputFlowData extends FlowData implements PositionHolder {

	public Position position;
	public List<UUID> tileEntityRules;

	public TileOutputFlowData(UUID uuid, Position position, List<UUID> ters) {
		super(uuid);
		this.position = position;
		this.tileEntityRules = ters;
	}

	public TileOutputFlowData(CompoundNBT tag) {
		this(null, new Position(), new ArrayList<>());
		deserializeNBT(tag);
	}

	@Override
	public CompoundNBT serializeNBT() {
		CompoundNBT tag = super.serializeNBT();
		tag.put("pos", position.serializeNBT());
		tag.put("ters", tileEntityRules.stream()
			.map(UUID::toString)
			.map(StringNBT::valueOf)
			.collect(ListNBT::new, ListNBT::add, ListNBT::addAll));
		FlowDataFactories.INPUT.stampNBT(tag);
		return tag;
	}

	@Override
	public void merge(FlowData other) {
		if (other instanceof TileOutputFlowData) {
			position = ((TileOutputFlowData) other).position;
			tileEntityRules = ((TileOutputFlowData) other).tileEntityRules;
		}
	}

	@Override
	public void deserializeNBT(CompoundNBT tag) {
		super.deserializeNBT(tag);
		this.position.deserializeNBT(tag.getCompound("pos"));
		this.tileEntityRules = tag.getList("ters", NBT.TAG_STRING).stream()
			.map(INBT::getString)
			.map(UUID::fromString)
			.collect(Collectors.toList());
	}

	@Override
	public FlowComponent createController(
		FlowComponent parent
	) {
		if (!(parent instanceof ManagerFlowController)) {
			return null;
		}
		return new FlowOutputButton((ManagerFlowController) parent, this);
	}

	@Override
	public FlowData copy() {
		return new TileOutputFlowData(getId(), getPosition(), tileEntityRules);
	}

	@Override
	public Position getPosition() {
		return position;
	}

	public static class FlowOutputDataFactory extends FlowDataFactory<TileOutputFlowData> {

		public FlowOutputDataFactory(ResourceLocation key) {
			super(key);
		}

		@Override
		public TileOutputFlowData fromNBT(CompoundNBT tag) {
			return new TileOutputFlowData(tag);
		}
	}
}
