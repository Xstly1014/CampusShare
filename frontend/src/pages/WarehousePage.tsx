import NavBar from '../components/common/NavBar'

export default function WarehousePage() {
  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 页面标题 */}
      <div className="bg-white border-b border-gray-200 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <h1 className="text-2xl font-bold text-gray-900">我的仓库</h1>
          <p className="text-gray-600 mt-1">管理您的上传和下载资料</p>
        </div>
      </div>

      {/* 主要内容区域 */}
      <div className="max-w-7xl mx-auto px-4 py-6">
        {/* 标签切换 */}
        <div className="bg-white rounded-lg shadow mb-6">
          <div className="border-b border-gray-200">
            <nav className="flex">
              <button
                className="px-6 py-3 text-blue-600 border-b-2 border-blue-600 font-medium"
              >
                我的上传
              </button>
              <button
                className="px-6 py-3 text-gray-600 hover:text-gray-900 font-medium"
              >
                我的下载
              </button>
            </nav>
          </div>

          {/* 内容区域 */}
          <div className="p-6">
            <div className="text-center py-12">
              <p className="text-gray-500">暂无资料</p>
              <p className="text-gray-400 mt-2">开始上传您的第一份资料吧</p>
            </div>
          </div>
        </div>
      </div>

      {/* 底部导航栏 */}
      <NavBar />
    </div>
  )
}