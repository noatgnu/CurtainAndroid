if (typeof Plotly === 'undefined') {
    document.getElementById('loading').style.display = 'none';
    document.getElementById('error').style.display = 'block';
    if (window.AndroidBridge) {
        window.AndroidBridge.onPlotError('Plotly.js failed to load');
    }
} else {
    const plotData = {{PLOT_DATA}};
    const proteinName = {{PROTEIN_NAME}};
    const editMode = {{EDIT_MODE}};

    window.BarChart = {
        plotDiv: null,
        currentData: plotData,
        currentLayout: null,
        currentConfig: null,

        initialize: function() {
            this.plotDiv = document.getElementById('plot');

            if (!this.plotDiv) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotError('Plot container not found');
                }
                return;
            }

            if (!plotData || !plotData.data || plotData.data.length === 0) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotError('No data provided for bar chart');
                }
                return;
            }

            this.currentLayout = plotData.layout || this.getDefaultLayout();
            this.currentConfig = plotData.config || this.getDefaultConfig();

            this.currentLayout.width = window.innerWidth;
            this.currentLayout.height = window.innerHeight;

            this.setupResizeListener();
            this.render();
        },

        setupResizeListener: function() {
            const self = this;
            window.addEventListener('resize', function() {
                if (self.plotDiv && self.currentLayout) {
                    self.currentLayout.width = window.innerWidth;
                    self.currentLayout.height = window.innerHeight;
                    Plotly.relayout(self.plotDiv, {
                        width: window.innerWidth,
                        height: window.innerHeight
                    });
                }
            });
        },

        render: function() {
            const self = this;

            Plotly.newPlot(
                this.plotDiv,
                this.currentData.data,
                this.currentLayout,
                this.currentConfig
            ).then(function() {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotReady('ready');
                }

                self.setupEventListeners();
            }).catch(function(error) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotError('Failed to create plot: ' + error.message);
                }
            });
        },

        setupEventListeners: function() {
            const self = this;

            if (this.plotDiv) {
                this.plotDiv.on('plotly_click', function(data) {
                    if (data.points && data.points.length > 0) {
                        const point = data.points[0];
                        const sampleName = point.x;
                        const value = point.y;
                        const condition = point.data.name;

                        if (window.AndroidBridge) {
                            const clickData = JSON.stringify({
                                sampleName: sampleName,
                                value: value,
                                condition: condition,
                                protein: proteinName
                            });
                            window.AndroidBridge.onBarClicked(clickData);
                        }
                    }
                });

                this.plotDiv.on('plotly_hover', function(data) {
                    if (data.points && data.points.length > 0) {
                        const point = data.points[0];
                        if (window.AndroidBridge) {
                            const hoverData = JSON.stringify({
                                sampleName: point.x,
                                value: point.y,
                                condition: point.data.name
                            });
                            window.AndroidBridge.onBarHover(hoverData);
                        }
                    }
                });

                this.plotDiv.on('plotly_relayout', function(eventData) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onPlotUpdated('Layout updated');
                    }
                });
            }
        },

        updateData: function(newData) {
            if (!this.plotDiv) return;

            this.currentData = newData;

            Plotly.react(
                this.plotDiv,
                newData.data,
                newData.layout || this.currentLayout,
                newData.config || this.currentConfig
            ).then(function() {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotUpdated('Data updated');
                }
            }).catch(function(error) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotError('Failed to update data: ' + error.message);
                }
            });
        },

        updateLayout: function(layoutUpdate) {
            if (!this.plotDiv) return;

            Object.assign(this.currentLayout, layoutUpdate);

            Plotly.relayout(this.plotDiv, layoutUpdate).then(function() {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotUpdated('Layout updated');
                }
            }).catch(function(error) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotError('Failed to update layout: ' + error.message);
                }
            });
        },

        setYAxisRange: function(minY, maxY) {
            this.updateLayout({
                'yaxis.range': [minY, maxY]
            });
        },

        resetYAxisRange: function() {
            this.updateLayout({
                'yaxis.autorange': true
            });
        },

        exportToPNG: function(filename) {
            if (!this.plotDiv) return;

            Plotly.toImage(this.plotDiv, {
                format: 'png',
                width: this.currentLayout.width || 1200,
                height: this.currentLayout.height || 800,
                filename: filename || 'bar_chart'
            }).then(function(url) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onImageExported(url);
                }
            }).catch(function(error) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotError('Failed to export image: ' + error.message);
                }
            });
        },

        exportToSVG: function(filename) {
            if (!this.plotDiv) return;

            Plotly.toImage(this.plotDiv, {
                format: 'svg',
                width: this.currentLayout.width || 1200,
                height: this.currentLayout.height || 800,
                filename: filename || 'bar_chart'
            }).then(function(url) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onImageExported(url);
                }
            }).catch(function(error) {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPlotError('Failed to export image: ' + error.message);
                }
            });
        },

        getDefaultLayout: function() {
            return {
                title: 'Protein Expression',
                xaxis: {
                    title: 'Samples',
                    showgrid: true
                },
                yaxis: {
                    title: 'Intensity',
                    showgrid: true
                },
                showlegend: true,
                legend: {
                    x: 1,
                    y: 1
                },
                hovermode: 'closest',
                plot_bgcolor: 'rgba(0,0,0,0)',
                paper_bgcolor: 'rgba(0,0,0,0)'
            };
        },

        getDefaultConfig: function() {
            return {
                displayModeBar: true,
                displaylogo: false,
                responsive: true,
                modeBarButtonsToRemove: [
                    'sendDataToCloud',
                    'editInChartStudio',
                    'lasso2d',
                    'select2d'
                ]
            };
        },

        destroy: function() {
            if (this.plotDiv) {
                Plotly.purge(this.plotDiv);
                this.plotDiv = null;
            }
        }
    };
}
