if (typeof Plotly === 'undefined') {
    document.getElementById('loading').style.display = 'none';
    document.getElementById('error').style.display = 'block';
    document.getElementById('error').innerHTML = '<div><h3>Plot Library Error</h3><p>Unable to load plotting library. Please try again.</p></div>';
    if (window.AndroidBridge) {
        window.AndroidBridge.onPlotError('Plotly.js failed to load');
    }
} else {
    Plotly.setPlotConfig({
        displayModeBar: true,
        displaylogo: false,
        modeBarButtonsToRemove: ['sendDataToCloud', 'editInChartStudio']
    });

    const plotData = {{PLOT_DATA}};
    const editMode = {{EDIT_MODE}};

    let currentPlot = null;
    let selectedPoints = [];
    let annotations = plotData.layout.annotations || [];

    const annotationMap = new Map();

    window.VolcanoPlot = {
        initialize: function() {
            try {
                document.getElementById('loading').style.display = 'none';
                document.getElementById('error').style.display = 'none';
                document.getElementById('plot').style.display = 'block';

                const plotDiv = document.getElementById('plot');
                const containerWidth = plotDiv.offsetWidth || window.innerWidth;
                const containerHeight = plotDiv.offsetHeight || window.innerHeight;

                plotData.layout.width = containerWidth;
                plotData.layout.height = containerHeight;

                Plotly.newPlot('plot', plotData.data, plotData.layout, plotData.config)
                    .then(() => {
                        currentPlot = document.getElementById('plot');
                        this.initializeAnnotationMap();
                        this.setupEventHandlers();

                        setTimeout(() => {
                            this.resizePlot();
                            this.notifyReady();
                        }, 100);

                        window.addEventListener('resize', () => {
                            this.resizePlot();
                        });
                    })
                    .catch(error => {
                        this.showError('Failed to create volcano plot: ' + error.message);
                    });
            } catch (error) {
                this.showError('JavaScript error: ' + error.message);
            }
        },

        getPlotDimensions: function() {
            if (!currentPlot || !currentPlot._fullLayout) {
                return null;
            }

            const layout = currentPlot._fullLayout;
            const plotDiv = currentPlot;

            const body = document.body || document.documentElement;
            const html = document.documentElement;

            const webViewRect = body.getBoundingClientRect();
            const webViewScrollLeft = html.scrollLeft || body.scrollLeft || 0;
            const webViewScrollTop = html.scrollTop || body.scrollTop || 0;

            const plotElementRect = plotDiv.getBoundingClientRect();
            const plotElementOffsetX = plotElementRect.left - webViewRect.left + webViewScrollLeft;
            const plotElementOffsetY = plotElementRect.top - webViewRect.top + webViewScrollTop;

            const xaxis = layout.xaxis;
            const yaxis = layout.yaxis;

            if (!xaxis || !yaxis || !xaxis.range || !yaxis.range) {
                return null;
            }

            try {
                const margin = layout.margin || {};
                const marginLeft = margin.l || 80;
                const marginTop = margin.t || 100;
                const marginRight = margin.r || 80;
                const marginBottom = margin.b || 80;

                const xMin = xaxis.range[0];
                const xMax = xaxis.range[1];
                const yMin = yaxis.range[0];
                const yMax = yaxis.range[1];

                const plotRelativeTopLeft = {
                    x: xaxis.l2p(xMin),
                    y: yaxis.l2p(yMax)
                };
                const plotRelativeBottomRight = {
                    x: xaxis.l2p(xMax),
                    y: yaxis.l2p(yMin)
                };

                const plotAreaLeft = marginLeft + plotRelativeTopLeft.x;
                const plotAreaTop = marginTop + plotRelativeTopLeft.y;
                const plotAreaRight = marginLeft + plotRelativeBottomRight.x;
                const plotAreaBottom = marginTop + plotRelativeBottomRight.y;

                const finalPlotLeft = webViewRect.left + plotElementOffsetX + plotAreaLeft;
                const finalPlotTop = webViewRect.top + plotElementOffsetY + plotAreaTop;
                const finalPlotRight = webViewRect.left + plotElementOffsetX + plotAreaRight;
                const finalPlotBottom = webViewRect.top + plotElementOffsetY + plotAreaBottom;

                return {
                    plotLeft: finalPlotLeft,
                    plotRight: finalPlotRight,
                    plotTop: finalPlotTop,
                    plotBottom: finalPlotBottom,

                    webView: {
                        left: webViewRect.left,
                        top: webViewRect.top,
                        width: webViewRect.width,
                        height: webViewRect.height
                    },
                    plotElement: {
                        offsetX: plotElementOffsetX,
                        offsetY: plotElementOffsetY,
                        width: plotElementRect.width,
                        height: plotElementRect.height
                    },
                    plotArea: {
                        left: plotAreaLeft,
                        top: plotAreaTop,
                        right: plotAreaRight,
                        bottom: plotAreaBottom,
                        width: plotAreaRight - plotAreaLeft,
                        height: plotAreaBottom - plotAreaTop
                    },

                    fullWidth: plotDiv.offsetWidth,
                    fullHeight: plotDiv.offsetHeight,

                    xRange: xaxis.range,
                    yRange: yaxis.range,

                    method: 'complete_hierarchy_l2p',
                    hasL2P: !!(xaxis.l2p && yaxis.l2p),
                    coordinateHierarchy: 'webView->plotElement->plotArea'
                };

            } catch (error) {
                const xDomain = xaxis.domain || [0, 1];
                const yDomain = yaxis.domain || [0, 1];

                const plotAreaLeft = xDomain[0] * plotDiv.offsetWidth;
                const plotAreaRight = xDomain[1] * plotDiv.offsetWidth;
                const plotAreaTop = (1 - yDomain[1]) * plotDiv.offsetHeight;
                const plotAreaBottom = (1 - yDomain[0]) * plotDiv.offsetHeight;

                const finalPlotLeft = webViewRect.left + plotElementOffsetX + plotAreaLeft;
                const finalPlotTop = webViewRect.top + plotElementOffsetY + plotAreaTop;
                const finalPlotRight = webViewRect.left + plotElementOffsetX + plotAreaRight;
                const finalPlotBottom = webViewRect.top + plotElementOffsetY + plotAreaBottom;

                return {
                    plotLeft: finalPlotLeft,
                    plotRight: finalPlotRight,
                    plotTop: finalPlotTop,
                    plotBottom: finalPlotBottom,

                    webView: {
                        left: webViewRect.left,
                        top: webViewRect.top,
                        width: webViewRect.width,
                        height: webViewRect.height
                    },
                    plotElement: {
                        offsetX: plotElementOffsetX,
                        offsetY: plotElementOffsetY,
                        width: plotElementRect.width,
                        height: plotElementRect.height
                    },
                    plotArea: {
                        left: plotAreaLeft,
                        top: plotAreaTop,
                        right: plotAreaRight,
                        bottom: plotAreaBottom,
                        width: plotAreaRight - plotAreaLeft,
                        height: plotAreaBottom - plotAreaTop
                    },

                    fullWidth: plotDiv.offsetWidth,
                    fullHeight: plotDiv.offsetHeight,
                    xRange: xaxis.range,
                    yRange: yaxis.range,
                    method: 'complete_hierarchy_domain_fallback',
                    hasL2P: false,
                    coordinateHierarchy: 'webView->plotElement->plotArea'
                };
            }
        },

        convertPlotToScreen: function(x, y) {
            if (!currentPlot || !currentPlot._fullLayout) {
                return null;
            }

            const dims = this.getPlotDimensions();
            if (!dims) {
                return null;
            }

            const layout = currentPlot._fullLayout;
            const xaxis = layout.xaxis;
            const yaxis = layout.yaxis;

            if (!xaxis || !yaxis) {
                return null;
            }

            try {
                const plotRelativeX = xaxis.l2p(x);
                const plotRelativeY = yaxis.l2p(y);

                const margin = layout.margin || {};
                const marginLeft = margin.l || 80;
                const marginTop = margin.t || 100;

                const plotElementX = marginLeft + plotRelativeX;
                const plotElementY = marginTop + plotRelativeY;

                const finalScreenX = dims.webView.left + dims.plotElement.offsetX + plotElementX;
                const finalScreenY = dims.webView.top + dims.plotElement.offsetY + plotElementY;

                return {
                    x: finalScreenX,
                    y: finalScreenY,
                    hierarchy: {
                        plotRelative: { x: plotRelativeX, y: plotRelativeY },
                        plotElement: { x: plotElementX, y: plotElementY },
                        webViewOffset: { x: dims.webView.left, y: dims.webView.top },
                        elementOffset: { x: dims.plotElement.offsetX, y: dims.plotElement.offsetY }
                    }
                };

            } catch (error) {
                if (!dims.plotArea) {
                    return null;
                }

                const plotAreaWidth = dims.plotArea.width;
                const plotAreaHeight = dims.plotArea.height;

                const normalizedX = (x - dims.xRange[0]) / (dims.xRange[1] - dims.xRange[0]);
                const normalizedY = (dims.yRange[1] - y) / (dims.yRange[1] - dims.yRange[0]);

                const plotAreaX = normalizedX * plotAreaWidth;
                const plotAreaY = normalizedY * plotAreaHeight;

                const finalScreenX = dims.webView.left + dims.plotElement.offsetX + dims.plotArea.left + plotAreaX;
                const finalScreenY = dims.webView.top + dims.plotElement.offsetY + dims.plotArea.top + plotAreaY;

                return {
                    x: finalScreenX,
                    y: finalScreenY,
                    method: 'fallback_with_hierarchy'
                };
            }
        },

        initializeAnnotationMap: function() {
            annotationMap.clear();

            if (currentPlot && currentPlot.layout && currentPlot.layout.annotations) {
                annotations = currentPlot.layout.annotations;
            }

            const dims = this.getPlotDimensions();

            annotations.forEach((annotation, index) => {
                const annotationInfo = {
                    annotation: annotation,
                    index: index
                };

                let identifier = null;
                if (annotation.title) {
                    identifier = annotation.title;
                } else if (annotation.text) {
                    identifier = annotation.text.replace(/<[^>]*>/g, '');
                } else if (annotation.id) {
                    identifier = annotation.id;
                }

                if (identifier) {
                    annotationMap.set(identifier, annotationInfo);
                }
            });
        },

        setupEventHandlers: function() {
            if (!currentPlot) return;

            currentPlot.on('plotly_click', (data) => {
                if (data.points && data.points.length > 0) {
                    const point = data.points[0];
                    const clickData = {
                        proteinId: point.customdata.id,
                        id: point.customdata.id,
                        primaryID: point.customdata.id,
                        proteinName: point.customdata.gene,
                        log2FC: point.x,
                        pValue: point.customdata.pValue,
                        x: point.x,
                        y: point.y,
                        screenX: data.event.clientX,
                        screenY: data.event.clientY
                    };

                    this.notifyPointClicked(clickData);
                }
            });

            if (editMode) {
                this.enableAnnotationEditing();
            }

            currentPlot.on('plotly_hover', (data) => {
                if (data.points && data.points.length > 0) {
                    const point = data.points[0];
                    this.notifyPointHovered(point.customdata);
                }
            });
        },

        enableAnnotationEditing: function() {
        },

        resizePlot: function() {
            if (!currentPlot) return;

            const plotDiv = document.getElementById('plot');
            const containerWidth = plotDiv.offsetWidth || window.innerWidth;
            const containerHeight = plotDiv.offsetHeight || window.innerHeight;

            Plotly.relayout(currentPlot, {
                width: containerWidth,
                height: containerHeight
            });
        },

        updatePlot: function(newData) {
            try {
                if (currentPlot) {
                    Plotly.react(currentPlot, newData.data, newData.layout, newData.config)
                        .then(() => {
                            this.notifyUpdated();
                        })
                        .catch(error => {
                            this.showError('Failed to update plot: ' + error.message);
                        });
                }
            } catch (error) {
                this.showError('JavaScript error: ' + error.message);
            }
        },

        addAnnotation: function(annotation) {
            annotations.push(annotation);
            this.updateAnnotations();
        },

        updateAnnotations: function() {
            if (currentPlot) {
                const update = { 'annotations': annotations };
                Plotly.relayout(currentPlot, update);
            }
        },

        updateAnnotationPosition: function(annotationTitle, ax, ay) {
            if (!currentPlot) {
                return;
            }

            const annotationInfo = annotationMap.get(annotationTitle);

            if (annotationInfo) {
                annotationInfo.annotation.ax = ax;
                annotationInfo.annotation.ay = ay;

                const update = {
                    ['annotations[' + annotationInfo.index + '].ax']: ax,
                    ['annotations[' + annotationInfo.index + '].ay']: ay
                };

                try {
                    Plotly.relayout(currentPlot, update);
                } catch (error) {
                }
            } else {
                for (let [key, info] of annotationMap.entries()) {
                    if (info.annotation.text && info.annotation.text.includes(annotationTitle)) {
                        return this.updateAnnotationPosition(key, ax, ay);
                    }
                }
            }
        },

        updateAnnotationPositions: function(updates) {
            if (!currentPlot || !updates || updates.length === 0) return;

            const batchUpdate = {};
            let hasChanges = false;

            for (const update of updates) {
                const annotationInfo = annotationMap.get(update.title);

                if (annotationInfo) {
                    annotationInfo.annotation.ax = update.ax;
                    annotationInfo.annotation.ay = update.ay;

                    batchUpdate['annotations[' + annotationInfo.index + '].ax'] = update.ax;
                    batchUpdate['annotations[' + annotationInfo.index + '].ay'] = update.ay;
                    hasChanges = true;
                }
            }

            if (hasChanges) {
                Plotly.relayout(currentPlot, batchUpdate);
            }
        },

        showError: function(message) {
            document.getElementById('loading').style.display = 'none';
            document.getElementById('plot').style.display = 'none';
            const errorDiv = document.getElementById('error');
            errorDiv.innerHTML = '<div><h3>Volcano Plot Error</h3><p>' + message + '</p></div>';
            errorDiv.style.display = 'flex';
            this.notifyError(message);
        },

        notifyReady: function() {
            if (window.AndroidBridge) {
                window.AndroidBridge.onPlotReady('ready');
            }
        },

        notifyPointClicked: function(pointData) {
            if (window.AndroidBridge) {
                window.AndroidBridge.onPointClicked(JSON.stringify(pointData));
            }
        },

        notifyPointHovered: function(pointData) {
            if (window.AndroidBridge) {
                window.AndroidBridge.onPointHovered(JSON.stringify(pointData));
            }
        },

        notifyUpdated: function() {
            if (window.AndroidBridge) {
                window.AndroidBridge.onPlotUpdated('updated');
            }
        },

        notifyError: function(message) {
            if (window.AndroidBridge) {
                window.AndroidBridge.onPlotError(message);
            }
        },

        sendPlotDimensions: function() {
            const dims = this.getPlotDimensions();
            if (dims && window.AndroidBridge) {
                window.AndroidBridge.onPlotDimensions(JSON.stringify(dims));
            }
        },

        convertAndSendCoordinates: function(annotations) {
            const results = [];
            const dims = this.getPlotDimensions();

            if (dims) {
                for (const annotation of annotations) {
                    const screenPos = this.convertPlotToScreen(annotation.x, annotation.y);

                    if (screenPos) {
                        results.push({
                            id: annotation.id || annotation.title,
                            plotX: annotation.x,
                            plotY: annotation.y,
                            screenX: screenPos.x,
                            screenY: screenPos.y,
                            ax: annotation.ax || 0,
                            ay: annotation.ay || 0
                        });
                    }
                }
            }

            if (window.AndroidBridge) {
                window.AndroidBridge.onAnnotationCoordinates(JSON.stringify(results));
            }
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            window.VolcanoPlot.initialize();
        });
    } else {
        window.VolcanoPlot.initialize();
    }
}
