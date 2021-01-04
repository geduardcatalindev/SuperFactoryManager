/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ca.teamdman.sfm.client.gui.flow.impl.manager.flowdataholder;

import ca.teamdman.sfm.client.gui.flow.impl.manager.core.ManagerFlowController;
import ca.teamdman.sfm.client.gui.flow.impl.util.FlowIconButton;
import ca.teamdman.sfm.common.flow.core.FlowDataHolder;
import ca.teamdman.sfm.common.flow.data.LineNodeFlowData;
import ca.teamdman.sfm.common.flow.holder.FlowDataHolderObserver;
import ca.teamdman.sfm.common.net.PacketHandler;
import ca.teamdman.sfm.common.net.packet.manager.patch.ManagerPositionPacketC2S;

public class FlowLineNode extends FlowIconButton implements FlowDataHolder<LineNodeFlowData> {

	public final ManagerFlowController CONTROLLER;
	private LineNodeFlowData data;

	public FlowLineNode(ManagerFlowController controller, LineNodeFlowData data) {
		super(
			ButtonBackground.LINE_NODE,
			ButtonBackground.LINE_NODE,
			ButtonLabel.NONE,
			data.getPosition().copy()
		);
		this.data = data;
		this.CONTROLLER = controller;
		this.CONTROLLER.SCREEN.getFlowDataContainer().addObserver(new FlowDataHolderObserver<>(
			this,
			LineNodeFlowData.class
		));
	}

	@Override
	public LineNodeFlowData getData() {
		return data;
	}

	@Override
	public void setData(LineNodeFlowData data) {
		this.data = data;
		getPosition().setXY(data.getPosition());
	}


	@Override
	public void onDragFinished(int dx, int dy, int mx, int my) {
		PacketHandler.INSTANCE.sendToServer(new ManagerPositionPacketC2S(
			CONTROLLER.SCREEN.getContainer().windowId,
			CONTROLLER.SCREEN.getContainer().getSource().getPos(),
			data.getId(),
			this.getPosition()
		));
	}

	@Override
	public void onClicked(int mx, int my, int button) {

	}
}