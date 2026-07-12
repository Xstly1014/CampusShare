(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var accent3 = style.getPropertyValue('--accent3').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var bg3 = style.getPropertyValue('--bg3').trim();

  // --- Chart: MTEB Benchmark Comparison ---
  var chartMteb = echarts.init(document.getElementById('chart-mteb'), null, { renderer: 'svg' });
  chartMteb.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      backgroundColor: '#1a1d2e',
      borderColor: '#2e3348',
      textStyle: { color: '#e8eaed', fontFamily: 'CrimsonPro, serif' }
    },
    grid: {
      left: 50,
      right: 30,
      top: 30,
      bottom: 80
    },
    xAxis: {
      type: 'category',
      data: ['OpenAI\ntext-embedding-3-large', 'GTE-Qwen2\n7B', 'BGE-M3', 'E5-Mistral\n7B', 'Jina\nEmbeddings v3', 'Cohere\nembed-v3', 'E5-large\nv2'],
      axisLabel: {
        color: muted,
        fontSize: 11,
        fontFamily: 'JetBrainsMono, monospace',
        interval: 0,
        rotate: 0
      },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 60,
      max: 72,
      axisLabel: {
        color: muted,
        fontSize: 11,
        fontFamily: 'JetBrainsMono, monospace',
        formatter: '{value}'
      },
      splitLine: { lineStyle: { color: rule, type: 'dashed' } },
      axisLine: { show: false }
    },
    series: [{
      type: 'bar',
      barWidth: 36,
      data: [
        { value: 71.2, itemStyle: { color: accent } },
        { value: 70.8, itemStyle: { color: accent2 } },
        { value: 69.5, itemStyle: { color: accent + 'cc' } },
        { value: 68.9, itemStyle: { color: accent2 + 'cc' } },
        { value: 68.1, itemStyle: { color: accent + '99' } },
        { value: 67.3, itemStyle: { color: accent2 + '99' } },
        { value: 65.5, itemStyle: { color: muted + '99' } }
      ],
      label: {
        show: true,
        position: 'top',
        color: muted,
        fontSize: 11,
        fontFamily: 'JetBrainsMono, monospace',
        formatter: '{c}'
      }
    }]
  });
  window.addEventListener('resize', function() { chartMteb.resize(); });

  // --- Chart: Embedding Dimension vs Quality ---
  var chartDim = echarts.init(document.getElementById('chart-dimension'), null, { renderer: 'svg' });
  chartDim.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      backgroundColor: '#1a1d2e',
      borderColor: '#2e3348',
      textStyle: { color: '#e8eaed', fontFamily: 'CrimsonPro, serif' }
    },
    grid: {
      left: 50,
      right: 30,
      top: 30,
      bottom: 50
    },
    xAxis: {
      type: 'category',
      data: ['64', '128', '256', '512', '768', '1024', '1536', '2048', '3072'],
      axisLabel: {
        color: muted,
        fontSize: 11,
        fontFamily: 'JetBrainsMono, monospace'
      },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        name: '检索 Recall@10',
        nameTextStyle: { color: muted, fontSize: 11 },
        min: 0.50,
        max: 1.0,
        axisLabel: {
          color: muted,
          fontSize: 11,
          fontFamily: 'JetBrainsMono, monospace',
          formatter: '{value}'
        },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { show: false }
      },
      {
        type: 'value',
        name: '存储 (相对)',
        nameTextStyle: { color: muted, fontSize: 11 },
        min: 0,
        max: 5,
        axisLabel: {
          color: muted,
          fontSize: 11,
          fontFamily: 'JetBrainsMono, monospace',
          formatter: '{value}x'
        },
        splitLine: { show: false },
        axisLine: { show: false }
      }
    ],
    series: [
      {
        name: 'Recall@10',
        type: 'line',
        data: [0.52, 0.61, 0.72, 0.84, 0.90, 0.93, 0.95, 0.96, 0.97],
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: accent, width: 2.5 },
        itemStyle: { color: accent },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: accent + '40' },
              { offset: 1, color: accent + '05' }
            ]
          }
        }
      },
      {
        name: '存储成本',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: 20,
        data: [0.06, 0.13, 0.25, 0.50, 0.75, 1.0, 1.5, 2.0, 3.0],
        itemStyle: { color: accent2 + '55' }
      }
    ]
  });
  window.addEventListener('resize', function() { chartDim.resize(); });

})();
