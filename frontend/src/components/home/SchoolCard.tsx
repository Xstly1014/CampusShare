import { FileText } from 'lucide-react'

interface School {
  id: string
  name: string
  logo: string
  resourceCount: number
}

interface SchoolCardProps {
  school: School
  onClick: () => void
}

export default function SchoolCard({ school, onClick }: SchoolCardProps) {
  return (
    <div
      onClick={onClick}
      className="bg-white rounded-xl shadow-md hover:shadow-lg transition-all duration-300 cursor-pointer overflow-hidden group transform hover:-translate-y-1"
    >
      {/* 学校校徽 */}
      <div className="h-24 flex items-center justify-center bg-gradient-to-br from-blue-50 to-blue-100 p-4">
        <img
          src={school.logo}
          alt={school.name}
          className="w-16 h-16 object-contain rounded-lg group-hover:scale-110 transition-transform duration-300"
          onError={(e) => {
            // 如果图片加载失败，显示占位符
            const target = e.target as HTMLImageElement
            target.style.display = 'none'
            target.parentElement!.innerHTML = `
              <div class="w-16 h-16 bg-blue-200 rounded-lg flex items-center justify-center">
                <span class="text-blue-600 text-xl font-bold">${school.name.substring(0, 2)}</span>
              </div>
            `
          }}
        />
      </div>

      {/* 学校信息 */}
      <div className="p-4">
        <h3 className="text-base font-semibold text-gray-900 mb-2 truncate group-hover:text-blue-600 transition-colors">
          {school.name}
        </h3>
        <div className="flex items-center text-sm text-gray-600">
          <FileText className="w-4 h-4 mr-1" />
          <span>{school.resourceCount}份资料</span>
        </div>
      </div>
    </div>
  )
}