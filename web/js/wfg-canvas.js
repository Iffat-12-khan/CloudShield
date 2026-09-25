/**
 * File Location: web/js/wfg-canvas.js
 * Short Purpose: Interactive HTML5 Canvas Renderer for the Operating Systems Wait-For Graph (WFG).
 * Concepts:
 *   - OS Wait-For Graph (WFG): Directed graph representing processes waiting for resources held by others.
 *   - Cycle Visualization: Highlights circular wait paths in neon crimson with pulse animations.
 * Connections:
 *   - Loaded by web/index.html.
 *   - Fed graph data and cycle paths by web/js/app.js via /api/deadlock/graph and /api/deadlock/detect.
 */

class WFGVisualizer {
    constructor(canvasId) {
        this.canvas = document.getElementById(canvasId);
        if (!this.canvas) return;
        this.ctx = this.canvas.getContext('2d');
        
        this.nodes = [];       // { id, label, x, y, radius, isCycle }
        this.edges = [];       // { from, to, resourceId, resourceName, isCycle }
        this.cyclePaths = [];  // Array of cycle paths
        this.activeCycleNodeIds = new Set();
        this.activeCycleEdges = new Set(); // "from-to" keys

        this.isDragging = false;
        this.draggedNode = null;
        this.animFrameId = null;
        this.pulseTime = 0;

        this.initCanvasSize();
        this.attachEventListeners();
        this.startRenderLoop();
    }

    initCanvasSize() {
        const rect = this.canvas.parentElement.getBoundingClientRect();
        this.canvas.width = rect.width;
        this.canvas.height = 380;
    }

    attachEventListeners() {
        window.addEventListener('resize', () => this.initCanvasSize());

        this.canvas.addEventListener('mousedown', (e) => {
            const pos = this.getMousePos(e);
            for (let node of this.nodes) {
                const dx = pos.x - node.x;
                const dy = pos.y - node.y;
                if (Math.sqrt(dx * dx + dy * dy) <= node.radius + 5) {
                    this.isDragging = true;
                    this.draggedNode = node;
                    break;
                }
            }
        });

        this.canvas.addEventListener('mousemove', (e) => {
            if (this.isDragging && this.draggedNode) {
                const pos = this.getMousePos(e);
                this.draggedNode.x = Math.max(40, Math.min(this.canvas.width - 40, pos.x));
                this.draggedNode.y = Math.max(40, Math.min(this.canvas.height - 40, pos.y));
            }
        });

        window.addEventListener('mouseup', () => {
            this.isDragging = false;
            this.draggedNode = null;
        });
    }

    getMousePos(e) {
        const rect = this.canvas.getBoundingClientRect();
        return {
            x: e.clientX - rect.left,
            y: e.clientY - rect.top
        };
    }

    updateData(graphData, cycleDetectionResult) {
        if (!graphData) return;

        // Process cycles
        this.activeCycleNodeIds.clear();
        this.activeCycleEdges.clear();

        if (cycleDetectionResult && cycleDetectionResult.deadlockDetected && cycleDetectionResult.cycles) {
            cycleDetectionResult.cycles.forEach(cycle => {
                for (let i = 0; i < cycle.length - 1; i++) {
                    const fromId = cycle[i];
                    const toId = cycle[i + 1];
                    this.activeCycleNodeIds.add(fromId);
                    this.activeCycleNodeIds.add(toId);
                    this.activeCycleEdges.add(`${fromId}-${toId}`);
                }
            });
        }

        // Layout nodes on circular/orbital layout
        const rawNodes = graphData.nodes || [];
        const count = rawNodes.length;
        const centerX = this.canvas.width / 2;
        const centerY = this.canvas.height / 2;
        const radius = Math.min(centerX, centerY) - 70;

        const newNodes = [];
        rawNodes.forEach((rn, idx) => {
            // Keep existing position if dragged
            const existing = this.nodes.find(n => n.id === rn.id);
            let x, y;
            if (existing) {
                x = existing.x;
                y = existing.y;
            } else {
                const angle = (idx / count) * 2 * Math.PI - (Math.PI / 2);
                x = centerX + radius * Math.cos(angle);
                y = centerY + radius * Math.sin(angle);
            }

            newNodes.push({
                id: rn.id,
                label: rn.label,
                x: x,
                y: y,
                radius: 28,
                isCycle: this.activeCycleNodeIds.has(rn.id)
            });
        });
        this.nodes = newNodes;

        // Edges
        this.edges = (graphData.edges || []).map(re => ({
            from: re.from,
            to: re.to,
            resourceId: re.resourceId,
            resourceName: re.resourceName,
            isCycle: this.activeCycleEdges.has(`${re.from}-${re.to}`)
        }));
    }

    startRenderLoop() {
        const render = () => {
            this.pulseTime += 0.05;
            this.draw();
            this.animFrameId = requestAnimationFrame(render);
        };
        render();
    }

    draw() {
        const ctx = this.ctx;
        ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        // Draw background grid dots
        this.drawGrid(ctx);

        if (this.nodes.length === 0) {
            ctx.fillStyle = '#64748b';
            ctx.font = '14px Inter, sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('No active processes or wait dependencies in graph.', this.canvas.width / 2, this.canvas.height / 2);
            return;
        }

        // 1. Draw directed edges with arrow heads
        this.edges.forEach(edge => {
            const fromNode = this.nodes.find(n => n.id === edge.from);
            const toNode = this.nodes.find(n => n.id === edge.to);
            if (fromNode && toNode) {
                this.drawArrow(ctx, fromNode, toNode, edge);
            }
        });

        // 2. Draw nodes (Virtual Machines)
        this.nodes.forEach(node => {
            this.drawNode(ctx, node);
        });
    }

    drawGrid(ctx) {
        ctx.fillStyle = 'rgba(139, 92, 246, 0.05)';
        const step = 30;
        for (let x = 15; x < this.canvas.width; x += step) {
            for (let y = 15; y < this.canvas.height; y += step) {
                ctx.beginPath();
                ctx.arc(x, y, 1, 0, Math.PI * 2);
                ctx.fill();
            }
        }
    }

    drawArrow(ctx, from, to, edge) {
        const dx = to.x - from.x;
        const dy = to.y - from.y;
        const angle = Math.atan2(dy, dx);
        const dist = Math.sqrt(dx * dx + dy * dy);

        // Shorten to not overlap node circles
        const startX = from.x + Math.cos(angle) * from.radius;
        const startY = from.y + Math.sin(angle) * from.radius;
        const endX = to.x - Math.cos(angle) * (to.radius + 6);
        const endY = to.y - Math.sin(angle) * (to.radius + 6);

        const isCycle = edge.isCycle;
        const strokeColor = isCycle ? '#ef4444' : '#8b5cf6';
        const glowColor = isCycle ? 'rgba(239, 68, 68, 0.6)' : 'rgba(139, 92, 246, 0.35)';

        ctx.save();
        ctx.strokeStyle = strokeColor;
        ctx.lineWidth = isCycle ? 3.5 : 2;
        ctx.shadowColor = glowColor;
        ctx.shadowBlur = isCycle ? 12 : 6;

        // Draw curved line for smooth visualization
        const midX = (startX + endX) / 2 + Math.sin(angle) * 16;
        const midY = (startY + endY) / 2 - Math.cos(angle) * 16;

        ctx.beginPath();
        ctx.moveTo(startX, startY);
        ctx.quadraticCurveTo(midX, midY, endX, endY);
        ctx.stroke();

        // Arrow head
        const arrowHeadAngle = Math.atan2(endY - midY, endX - midX);
        const arrowSize = isCycle ? 10 : 8;
        ctx.fillStyle = strokeColor;
        ctx.beginPath();
        ctx.moveTo(endX, endY);
        ctx.lineTo(endX - arrowSize * Math.cos(arrowHeadAngle - Math.PI / 6),
                   endY - arrowSize * Math.sin(arrowHeadAngle - Math.PI / 6));
        ctx.lineTo(endX - arrowSize * Math.cos(arrowHeadAngle + Math.PI / 6),
                   endY - arrowSize * Math.sin(arrowHeadAngle + Math.PI / 6));
        ctx.closePath();
        ctx.fill();

        // Resource badge on edge
        if (edge.resourceName) {
            ctx.font = '10px Inter, sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            const text = edge.resourceName;
            const textWidth = ctx.measureText(text).width + 12;

            ctx.fillStyle = 'rgba(14, 9, 32, 0.9)';
            ctx.strokeStyle = isCycle ? '#ef4444' : 'rgba(139, 92, 246, 0.5)';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.roundRect(midX - textWidth / 2, midY - 9, textWidth, 18, 9);
            ctx.fill();
            ctx.stroke();

            ctx.fillStyle = isCycle ? '#fca5a5' : '#c084fc';
            ctx.fillText(text, midX, midY);
        }

        ctx.restore();
    }

    drawNode(ctx, node) {
        ctx.save();
        const isCycle = node.isCycle;
        const pulse = isCycle ? Math.sin(this.pulseTime) * 3 : 0;
        const radius = node.radius + pulse;

        // Outer Glow
        const grad = ctx.createRadialGradient(node.x, node.y, radius * 0.4, node.x, node.y, radius + 15);
        if (isCycle) {
            grad.addColorStop(0, 'rgba(239, 68, 68, 0.35)');
            grad.addColorStop(1, 'rgba(239, 68, 68, 0.0)');
        } else {
            grad.addColorStop(0, 'rgba(139, 92, 246, 0.25)');
            grad.addColorStop(1, 'rgba(139, 92, 246, 0.0)');
        }
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(node.x, node.y, radius + 15, 0, Math.PI * 2);
        ctx.fill();

        // Circle Base
        ctx.fillStyle = isCycle ? '#2a0a14' : '#140c2e';
        ctx.strokeStyle = isCycle ? '#ef4444' : '#a855f7';
        ctx.lineWidth = isCycle ? 3 : 2;
        ctx.shadowColor = isCycle ? '#ef4444' : '#8b5cf6';
        ctx.shadowBlur = isCycle ? 15 : 8;

        ctx.beginPath();
        ctx.arc(node.x, node.y, radius, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();

        // Node VM ID label
        ctx.shadowBlur = 0;
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 12px Inter, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(`VM-${node.id}`, node.x, node.y - 4);

        // Subtitle status or small name
        ctx.font = '9px Inter, sans-serif';
        ctx.fillStyle = isCycle ? '#fca5a5' : '#94a3b8';
        ctx.fillText(isCycle ? 'CYCLE' : 'WAIT', node.x, node.y + 9);

        // VM Full Name below node
        ctx.font = '11px Inter, sans-serif';
        ctx.fillStyle = '#e2e8f0';
        ctx.fillText(node.label, node.x, node.y + radius + 14);

        ctx.restore();
    }
}
