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
      className="bg-white rounded-2xl border border-gray-100 hover:border-blue-200 hover:shadow-md transition-all duration-200 cursor-pointer group"
    >
      <div className="p-5 flex flex-col items-center text-center">
        <div className="w-16 h-16 mb-3 flex items-center justify-center">
          <img
            src={school.logo}
            alt={school.name}
            className="w-full h-full object-contain group-hover:scale-110 transition-transform duration-200"
            onError={(e) => {
              const target = e.target as HTMLImageElement
              target.style.display = 'none'
              const parent = target.parentElement
              if (parent) {
                parent.innerHTML = `
                  <div class="w-16 h-16 bg-gradient-to-br from-blue-50 to-blue-100 rounded-2xl flex items-center justify-center">
                    <span class="text-blue-600 text-lg font-bold">${school.name.substring(0, 2)}</span>
                  </div>
                `
              }
            }}
          />
        </div>
        <h3 className="text-sm font-medium text-gray-900 mb-1 group-hover:text-blue-600 transition-colors">
          {school.name}
        </h3>
        <div className="flex items-center text-xs text-gray-400">
          <FileText className="w-3 h-3 mr-1" />
          <span>{school.resourceCount} 份资料</span>
        </div>
      </div>
    </div>
  )
}
