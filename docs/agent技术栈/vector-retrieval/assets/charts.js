(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  // --- Radar Chart: ANN Algorithm Comparison ---
  var radarChart = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });

  // Scores (1-10 scale): Build Speed, Search Speed, Recall, Memory Efficiency, Scalability
  var indicators = [
    { name: '构建速度', max: 10 },
    { name: '搜索速度', max: 10 },
    { name: '召回率', max: 10 },
    { name: '内存效率', max: 10 },
    { name: '可扩展性', max: 10 }
  ];

  var seriesData = [
    { name: 'IVF_FLAT', value: [6, 5, 8, 3, 5] },
    { name: 'IVF_PQ', value: [4, 7, 5, 9, 8] },
    { name: 'HNSW', value: [3, 9, 9, 3, 6] },
    { name: 'ANNOY', value: [9, 6, 5, 6, 5] },
    { name: 'DiskANN', value: [3, 6, 8, 9, 10] }
  ];

  radarChart.setOption({
    animation: false,
    tooltip: {
      trigger: 'item',
      appendToBody: true,
      backgroundColor: bg2,
      borderColor: rule,
      textStyle: { color: ink, fontSize: 13 }
    },
    legend: {
      data: seriesData.map(function(d) { return d.name; }),
      bottom: 10,
      textStyle: { color: muted, fontSize: 12 },
      itemWidth: 14,
      itemHeight: 8
    },
    radar: {
      indicator: indicators,
      shape: 'polygon',
      center: ['50%', '46%'],
      radius: '65%',
      axisName: {
        color: muted,
        fontSize: 12,
        fontWeight: 600
      },
      splitArea: {
        show: true,
        areaStyle: {
          color: [bg2, 'rgba(30,41,59,0.6)']
        }
      },
      axisLine: {
        lineStyle: { color: rule }
      },
      splitLine: {
        lineStyle: { color: rule }
      }
    },
    series: [{
      type: 'radar',
      data: [
        {
          name: 'IVF_FLAT',
          value: seriesData[0].value,
          lineStyle: { color: accent, width: 2 },
          itemStyle: { color: accent },
          areaStyle: { color: accent + '20' }
        },
        {
          name: 'IVF_PQ',
          value: seriesData[1].value,
          lineStyle: { color: accent2, width: 2 },
          itemStyle: { color: accent2 },
          areaStyle: { color: accent2 + '20' }
        },
        {
          name: 'HNSW',
          value: seriesData[2].value,
          lineStyle: { color: '#34d399', width: 2 },
          itemStyle: { color: '#34d399' },
          areaStyle: { color: 'rgba(52,211,153,0.12)' }
        },
        {
          name: 'ANNOY',
          value: seriesData[3].value,
          lineStyle: { color: '#fbbf24', width: 2 },
          itemStyle: { color: '#fbbf24' },
          areaStyle: { color: 'rgba(251,191,36,0.12)' }
        },
        {
          name: 'DiskANN',
          value: seriesData[4].value,
          lineStyle: { color: '#f87171', width: 2 },
          itemStyle: { color: '#f87171' },
          areaStyle: { color: 'rgba(248,113,113,0.12)' }
        }
      ],
      emphasis: {
        lineStyle: { width: 3 }
      }
    }]
  });

  window.addEventListener('resize', function() { radarChart.resize(); });
})();
