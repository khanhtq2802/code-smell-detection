/*******************************************************************************
 * Copyright (c) 2018 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.elk.alg.layered.p5edges.loops.labeling;

import java.util.List;

import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopComponent;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopEdge;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopLabel;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopLabelPosition;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopNode;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopPort;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopRoutingDirection;
import org.eclipse.elk.alg.layered.p5edges.loops.util.SelfLoopBendpointCalculationUtil;
import org.eclipse.elk.alg.layered.p5edges.splines.SplinesMath;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.options.PortSide;

import com.google.common.collect.Iterables;

/**
 * Generates self loop label positions for three-corner self loops.
 */
public class ThreeCornerLoopLabelPositionGenerator extends AbstractSelfLoopLabelPositionGenerator {

    /**
     * Creates a new instance for the given node.
     */
    public ThreeCornerLoopLabelPositionGenerator(final SelfLoopNode slNode) {
        super(slNode);
    }

    @Override
    public void generatePositions(final SelfLoopComponent component) {
        List<SelfLoopPort> ports = component.getPorts();
        SelfLoopPort startPort = ports.get(0);
        SelfLoopPort endPort = ports.get(ports.size() - 1);
        
        // Retrieve the spacings active for this node
        double edgeEdgeSpacing = getEdgeEdgeSpacing();
        double edgeLabelSpacing = getEdgeLabelSpacing();

        // Generate all the bend points
        KVector startPosition = startPort.getLPort().getPosition().clone().add(startPort.getLPort().getAnchor());
        KVector endPosition = endPort.getLPort().getPosition().clone().add(endPort.getLPort().getAnchor());

        double directionStart = SplinesMath.portSideToDirection(startPort.getPortSide());
        KVector dirVectorStart = new KVector(directionStart);
        
        double directionEnd = SplinesMath.portSideToDirection(endPort.getPortSide());
        KVector dirVectorEnd = new KVector(directionEnd);
        
        KVector firstBend = startPosition.clone().add(dirVectorStart.clone().scale(
                (startPort.getMaximumLevel() * edgeEdgeSpacing) + edgeLabelSpacing));
        KVector secondBend = endPosition.clone().add(dirVectorEnd.clone().scale(
                (endPort.getMaximumLevel() * edgeEdgeSpacing) + edgeLabelSpacing));

        SelfLoopEdge edge = Iterables.get(component.getConnectedEdges(), 0);
        List<KVector> cornerBends = SelfLoopBendpointCalculationUtil.generateCornerBendpoints(
                getSelfLoopNode(), startPort, endPort, firstBend, secondBend, edge);

        // Generate all the segment sides
        // SUPPRESS CHECKSTYLE NEXT MagicNumber
        PortSide[] segmentSides = new PortSide[2];
        PortSide startSide = startPort.getPortSide();
        
        if (startPort.getDirection() == SelfLoopRoutingDirection.RIGHT) {
            segmentSides[0] = startSide.right();
            segmentSides[1] = segmentSides[0].right();
        } else {
            segmentSides[0] = startSide.left();
            segmentSides[1] = segmentSides[0].left();
        }
        
        addPositions(component, startPort, endPort, segmentSides, firstBend, cornerBends, secondBend);
    }

    private void addPositions(final SelfLoopComponent component, final SelfLoopPort startPort,
            final SelfLoopPort endPort, final PortSide[] segmentSides, final KVector firstBend,
            final List<KVector> cornerBends, final KVector lastBend) {
        
        SelfLoopLabel label = component.getSelfLoopLabel();
        List<SelfLoopLabelPosition> positions = label.getCandidatePositions();
        
        // SUPPRESS CHECKSTYLE NEXT 30 MagicNumber
        
        // Full segment 1 (long)
        positions.add(longSegmentPosition(
                label, segmentSides[0], cornerBends.get(0), cornerBends.get(1), Alignment.CENTERED));
        positions.add(longSegmentPosition(
                label, segmentSides[0], cornerBends.get(0), cornerBends.get(1), Alignment.LEFT_OR_TOP));
        positions.add(longSegmentPosition(
                label, segmentSides[0], cornerBends.get(0), cornerBends.get(1), Alignment.RIGHT_OR_BOTTOM));
        
        // Full segment 2 (long)
        positions.add(longSegmentPosition(
                label, segmentSides[0], cornerBends.get(1), cornerBends.get(2), Alignment.CENTERED));
        positions.add(longSegmentPosition(
                label, segmentSides[0], cornerBends.get(1), cornerBends.get(2), Alignment.LEFT_OR_TOP));
        positions.add(longSegmentPosition(
                label, segmentSides[0], cornerBends.get(1), cornerBends.get(2), Alignment.RIGHT_OR_BOTTOM));
        
        // Start segment (short)
        positions.add(shortSegmentPosition(
                label, startPort, firstBend, cornerBends.get(0), Alignment.CENTERED, true));
        positions.add(shortSegmentPosition(
                label, startPort, firstBend, cornerBends.get(0), Alignment.LEFT_OR_TOP, true));
        positions.add(shortSegmentPosition(
                label, startPort, firstBend, cornerBends.get(0), Alignment.RIGHT_OR_BOTTOM, true));
        
        // End segment (short)
        positions.add(shortSegmentPosition(
                label, endPort, lastBend, cornerBends.get(2), Alignment.CENTERED, true));
        positions.add(shortSegmentPosition(
                label, endPort, lastBend, cornerBends.get(2), Alignment.LEFT_OR_TOP, true));
        positions.add(shortSegmentPosition(
                label, endPort, lastBend, cornerBends.get(2), Alignment.RIGHT_OR_BOTTOM, true));
    }

}
