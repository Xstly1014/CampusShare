(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var bg3 = style.getPropertyValue('--bg3').trim();

  // --- Chart 1: Index Type Comparison (Data Volume vs Query Latency) ---
  var chart1 = echarts.init(document.getElementById('chart-index-compare'), null, { renderer: 'svg' });
  chart1.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      backgroundColor: bg3,
      borderColor: rule,
      textStyle: { color: ink, fontSize: 13 }
    },
    legend: {
      top: 0,
      textStyle: { color: muted, fontSize: 12 },
      itemGap: 16
    },
    grid: { left: 50, right: 30, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: ['1K', '10K', '100K', '1M', '10M', '100M'],
      name: '数据量',
      nameTextStyle: { color: muted, fontSize: 12 },
      axisLine: { lineStyle: { color: rule } },
      axisLabel: { color: muted, fontSize: 11 }
    },
    yAxis: {
      type: 'log',
      name: '查询延迟 (ms)',
      nameTextStyle: { color: muted, fontSize: 12 },
      axisLine: { lineStyle: { color: rule } },
      splitLine: { lineStyle: { color: rule, type: 'dashed' } },
      axisLabel: { color: muted, fontSize: 11, formatter: function(v) { return v >= 1 ? v + 'ms' : v; } }
    },
    series: [
      {
        name: 'FLAT',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2 },
        itemStyle: { color: '#ef4444' },
        data: [0.05, 0.5, 5, 50, 500, 5000]
      },
      {
        name: 'IVF_FLAT',
        type: 'line',
        smooth: true,
        symbol: 'diamond',
        symbolSize: 7,
        lineStyle: { width: 2 },
        itemStyle: { color: accent },
        data: [0.08, 0.6, 2, 5, 15, 80]
      },
      {
        name: 'HNSW',
        type: 'line',
        smooth: true,
        symbol: 'triangle',
        symbolSize: 7,
        lineStyle: { width: 2 },
        itemStyle: { color: accent2 },
        data: [0.04, 0.3, 0.8, 2, 5, 12]
      },
      {
        name: 'IVF_PQ',
        type: 'line',
        smooth: true,
        symbol: 'rect',
        symbolSize: 6,
        lineStyle: { width: 2 },
        itemStyle: { color: '#a78bfa' },
        data: [0.1, 0.8, 3, 8, 25, 120]
      },
      {
        name: 'DiskANN',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, type: 'dashed' },
        itemStyle: { color: '#06b6d4' },
        data: [1, 2, 5, 8, 15, 30]
      }
    ]
  });
  window.addEventListener('resize', function() { chart1.resize(); });

  // --- Chart 2: Index Selection Decision Matrix (Heatmap) ---
  var chart2 = echarts.init(document.getElementById('chart-index-matrix'), null, { renderer: 'svg' });
  var metrics = ['FLAT', 'IVF_FLAT', 'IVF_SQ8', 'IVF_PQ', 'HNSW', 'ANNOY', 'DiskANN'];
  var dims = ['召回率', '查询速度', '内存效率', '构建速度', '大规模适应性'];
  var data = [
    [0, 0, 10], [0, 1, 2],  [0, 2, 2],  [0, 3, 10], [0, 4, 2],
    [1, 0, 8],  [1, 1, 6],  [1, 2, 5],  [1, 3, 6],  [1, 4, 5],
    [2, 0, 7],  [2, 1, 7],  [2, 2, 7],  [2, 3, 6],  [2, 4, 5],
    [3, 0, 5],  [3, 1, 6],  [3, 2, 8],  [3, 3, 4],  [3, 4, 7],
    [4, 0, 9],  [4, 1, 9],  [4, 2, 4],  [4, 3, 3],  [4, 4, 6],
    [5, 0, 6],  [5, 1, 7],  [5, 2, 7],  [5, 3, 8],  [5, 4, 4],
    [6, 0, 7],  [6, 1, 6],  [6, 2, 9],  [6, 3, 3],  [6, 4, 9]
  ];

  chart2.setOption({
    animation: false,
    tooltip: {
      appendToBody: true,
      backgroundColor: bg3,
      borderColor: rule,
      textStyle: { color: ink, fontSize: 13 },
      formatter: function(p) {
        return metrics[p.data[0]] + ' - ' + dims[p.data[1]] + '：' + p.data[2] + '/10';
      }
    },
    grid: { left: 80, right: 40, top: 30, bottom: 60 },
    xAxis: {
      type: 'category',
      data: dims,
      axisLine: { lineStyle: { color: rule } },
      axisLabel: { color: muted, fontSize: 11, rotate: 15 },
      splitArea: { show: false }
    },
    yAxis: {
      type: 'category',
      data: metrics,
      axisLine: { lineStyle: { color: rule } },
      axisLabel: { color: muted, fontSize: 11 },
      splitArea: { show: false }
    },
    visualMap: {
      min: 0,
      max: 10,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: [bg2, accent + '99', accent] },
      textStyle: { color: muted, fontSize: 11 },
      text: ['高', '低']
    },
    series: [{
      type: 'heatmap',
      data: data,
      label: {
        show: true,
        color: ink,
        fontSize: 12,
        fontWeight: 'bold',
        formatter: function(p) { return p.data[2]; }
      },
      itemStyle: { borderColor: rule, borderWidth: 2, borderRadius: 4 }
    }]
  });
  window.addEventListener('resize', function() { chart2.resize(); });

  // --- Chart 3: Index Decision Flow (Tree diagram) ---
  var chart3 = echarts.init(document.getElementById('chart-index-decision'), null, { renderer: 'svg' });
  chart3.setOption({
    animation: false,
    tooltip: { appendToBody: true, backgroundColor: bg3, borderColor: rule, textStyle: { color: ink } },
    series: [{
      type: 'tree',
      data: [{
        name: '数据量?',
        children: [
          {
            name: '< 100万',
            itemStyle: { color: accent2 },
            children: [
              { name: '精度优先?', children: [
                { name: 'FLAT', itemStyle: { color: '#ef4444' } },
                { name: 'IVF_FLAT', itemStyle: { color: accent } }
              ]},
              { name: '延迟优先?', children: [
                { name: 'HNSW', itemStyle: { color: '#a78bfa' } }
              ]}
            ]
          },
          {
            name: '100万 ~ 10亿',
            itemStyle: { color: accent2 },
            children: [
              { name: '内存充裕?', children: [
                { name: 'HNSW', itemStyle: { color: '#a78bfa' } }
              ]},
              { name: '内存受限?', children: [
                { name: 'IVF_SQ8', itemStyle: { color: '#06b6d4' } },
                { name: 'IVF_PQ', itemStyle: { color: '#06b6d4' } }
              ]}
            ]
          },
          {
            name: '> 10亿',
            itemStyle: { color: accent2 },
            children: [
              { name: '内存充足?', children: [
                { name: 'HNSW + 分区', itemStyle: { color: '#a78bfa' } }
              ]},
              { name: 'SSD 可用?', children: [
                { name: 'DiskANN', itemStyle: { color: '#f472b6' } }
              ]}
            ]
          }
        ]
      }],
      top: '5%',
      left: '15%',
      bottom: '5%',
      right: '15%',
      symbol: 'roundRect',
      symbolSize: [120, 40],
      orient: 'LR',
      label: {
        position: 'inside',
        color: ink,
        fontSize: 12,
        fontWeight: 'bold'
      },
      leaves: {
        label: {
          position: 'inside',
          color: '#ffffff',
          fontSize: 13,
          fontWeight: 'bold'
        }
      },
      lineStyle: {
        color: rule,
        width: 2,
        curveness: 0.5
      },
      expandAndCollapse: false,
      initialTreeDepth: 10
    }]
  });
  window.addEventListener('resize', function() { chart3.resize(); });

  // --- Chart 4: Radar Chart - Vector DB Comparison ---
  var chart4 = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });
  chart4.setOption({
    animation: false,
    tooltip: {
      appendToBody: true,
      backgroundColor: bg3,
      borderColor: rule,
      textStyle: { color: ink, fontSize: 13 }
    },
    legend: {
      bottom: 0,
      textStyle: { color: muted, fontSize: 12 },
      itemGap: 14
    },
    radar: {
      indicator: [
        { name: '可扩展性', max: 10 },
        { name: '查询性能', max: 10 },
        { name: '索引丰富度', max: 10 },
        { name: '部署灵活度', max: 10 },
        { name: '易用性', max: 10 },
        { name: '生态集成', max: 10 }
      ],
      shape: 'polygon',
      axisName: { color: muted, fontSize: 12 },
      splitArea: { areaStyle: { color: [bg2, bg3] } },
      splitLine: { lineStyle: { color: rule } },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [{
      type: 'radar',
      data: [
        {
          name: 'Milvus',
          value: [10, 9, 10, 9, 6, 8],
          areaStyle: { color: accent + '44' },
          lineStyle: { color: accent, width: 2 },
          itemStyle: { color: accent }
        },
        {
          name: 'Pinecone',
          value: [7, 8, 6, 4, 10, 8],
          areaStyle: { color: '#ef444444' },
          lineStyle: { color: '#ef4444', width: 2 },
          itemStyle: { color: '#ef4444' }
        },
        {
          name: 'Weaviate',
          value: [6, 7, 5, 7, 7, 8],
          areaStyle: { color: accent2 + '44' },
          lineStyle: { color: accent2, width: 2 },
          itemStyle: { color: accent2 }
        },
        {
          name: 'Qdrant',
          value: [7, 8, 5, 8, 8, 7],
          areaStyle: { color: '#a78bfa44' },
          lineStyle: { color: '#a78bfa', width: 2 },
          itemStyle: { color: '#a78bfa' }
        },
        {
          name: 'Chroma',
          value: [2, 4, 4, 5, 10, 7],
          areaStyle: { color: '#06b6d444' },
          lineStyle: { color: '#06b6d4', width: 2 },
          itemStyle: { color: '#06b6d4' }
        }
      ]
    }]
  });
  window.addEventListener('resize', function() { chart4.resize(); });
})();
