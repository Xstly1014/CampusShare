import NavBar from '../components/common/NavBar'
import { useAuth } from '../context/AuthContext'

export default function ProfilePage() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 页面标题 */}
      <div className="bg-white border-b border-gray-200 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <h1 className="text-2xl font-bold text-gray-900">个人主页</h1>
          <p className="text-gray-600 mt-1">查看您的个人资料和动态</p>
        </div>
      </div>

      {/* 主要内容区域 */}
      <div className="max-w-7xl mx-auto px-4 py-6">
        {/* 个人信息卡片 */}
        <div className="bg-white rounded-lg shadow mb-6">
          <div className="p-6">
            <div className="flex items-center">
              <div className="w-20 h-20 bg-blue-600 rounded-full flex items-center justify-center">
                <span className="text-white text-2xl font-bold">
                  {user?.username?.substring(0, 2).toUpperCase() || 'U'}
                </span>
              </div>
              <div className="ml-6">
                <h2 className="text-xl font-bold text-gray-900">
                  {user?.username || '用户'}
                </h2>
                <p className="text-gray-600 mt-1">
                  {user?.email || user?.phone || '未绑定联系方式'}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* 统计数据 */}
        <div className="bg-white rounded-lg shadow mb-6">
          <div className="p-6">
            <div className="grid grid-cols-3 gap-4">
              <div className="text-center">
                <p className="text-2xl font-bold text-blue-600">0</p>
                <p className="text-gray-600 mt-1">上传资料</p>
              </div>
              <div className="text-center">
                <p className="text-2xl font-bold text-blue-600">0</p>
                <p className="text-gray-600 mt-1">下载次数</p>
              </div>
              <div className="text-center">
                <p className="text-2xl font-bold text-blue-600">0</p>
                <p className="text-gray-600 mt-1">获赞数量</p>
              </div>
            </div>
          </div>
        </div>

        {/* 退出登录按钮 */}
        <div className="bg-white rounded-lg shadow">
          <div className="p-6">
            <button
              onClick={() => {
                logout()
                window.location.href = '/'
              }}
              className="w-full py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
            >
              退出登录
            </button>
          </div>
        </div>
      </div>

      {/* 底部导航栏 */}
      <NavBar />
    </div>
  )
}