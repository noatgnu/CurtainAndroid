if (typeof Plotly === 'undefined') {
    document.getElementById('loading').style.display = 'none';
    document.getElementById('error').style.display = 'block';
    if (window.AndroidBridge) {
        window.AndroidBridge.onPlotError('Plotly.js failed to load');
    }
} else {
    Plotly.setPlotConfig({
        displayModeBar: false
    });

    const plotData = {{PLOT_DATA}};

    document.addEventListener('DOMContentLoaded', function() {
        try {
            document.getElementById('loading').style.display = 'none';
            document.getElementById('error').style.display = 'none';
            document.getElementById('plot').style.display = 'block';

            window.ProteinChart = {
                plotDiv: document.getElementById('plot'),
                exportPlot: function(format, filename) {
                    if (!this.plotDiv) return;
                    Plotly.toImage(this.plotDiv, {
                        format: format,
                        width: this.plotDiv.offsetWidth || 1200,
                        height: this.plotDiv.offsetHeight || 800
                    }).then(function(dataUrl) {
                        if (window.AndroidBridge) {
                            window.AndroidBridge.onImageExported(JSON.stringify({
                                format: format,
                                filename: filename || 'protein_chart',
                                dataUrl: dataUrl
                            }));
                        }
                    }).catch(function(error) {
                        if (window.AndroidBridge) {
                            window.AndroidBridge.onPlotError('Export failed: ' + error.message);
                        }
                    });
                }
            };

            Plotly.newPlot('plot', plotData.data, plotData.layout, plotData.config)
                .then(() => {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onPlotReady('ready');
                    }
                })
                .catch(error => {
                    document.getElementById('plot').style.display = 'none';
                    document.getElementById('error').style.display = 'flex';
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onPlotError(error.message || 'Error creating chart');
                    }
                });
        } catch (error) {
            document.getElementById('loading').style.display = 'none';
            document.getElementById('error').style.display = 'flex';
            if (window.AndroidBridge) {
                window.AndroidBridge.onPlotError(error.message || 'Error in initialization');
            }
        }
    });
}
