if (typeof Plotly === 'undefined') {
    document.getElementById('loading').style.display = 'none';
    document.getElementById('error').style.display = 'block';
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

    document.addEventListener('DOMContentLoaded', function() {
        try {
            document.getElementById('loading').style.display = 'none';
            document.getElementById('error').style.display = 'none';
            document.getElementById('plot').style.display = 'block';

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
