import { FileText, GraduationCap } from 'lucide-react'

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

const SCHOOL_COLORS = [
  { bg: 'bg-rose-50', text: 'text-rose-600' },
  { bg: 'bg-orange-50', text: 'text-orange-600' },
  { bg: 'bg-amber-50', text: 'text-amber-600' },
  { bg: 'bg-emerald-50', text: 'text-emerald-600' },
  { bg: 'bg-teal-50', text: 'text-teal-600' },
  { bg: 'bg-sky-50', text: 'text-sky-600' },
  { bg: 'bg-indigo-50', text: 'text-indigo-600' },
  { bg: 'bg-violet-50', text: 'text-violet-600' },
  { bg: 'bg-pink-50', text: 'text-pink-600' },
  { bg: 'bg-cyan-50', text: 'text-cyan-600' },
  { bg: 'bg-lime-50', text: 'text-lime-600' },
  { bg: 'bg-fuchsia-50', text: 'text-fuchsia-600' },
]

function hashCode(str: string): number {
  let h = 0
  for (let i = 0; i < str.length; i++) {
    h = (h << 5) - h + str.charCodeAt(i)
    h |= 0
  }
  return Math.abs(h)
}

export default function SchoolCard({ school, onClick }: SchoolCardProps) {
  const colorIdx = hashCode(school.id) % SCHOOL_COLORS.length
  const color = SCHOOL_COLORS[colorIdx]!

  return (
    <div
      onClick={onClick}
      className="bg-white rounded-2xl border border-gray-100 p-4 cursor-pointer hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group"
    >
      <div className="flex items-start gap-3">
        <div
          className={`w-12 h-12 rounded-xl ${color.bg} flex items-center justify-center flex-shrink-0 group-hover:scale-110 transition-transform`}
        >
          <GraduationCap className={`w-6 h-6 ${color.text}`} />
        </div>
        <div className="flex-1 min-w-0 pt-0.5">
          <h3 className="font-semibold text-gray-900 text-sm mb-1 group-hover:text-blue-600 transition-colors truncate">
            {school.name}
          </h3>
          <div className="flex items-center gap-1">
            <FileText className="w-3 h-3 text-gray-400" />
            <span className="text-xs text-gray-400">{school.resourceCount} 份资料</span>
          </div>
        </div>
      </div>
    </div>
  )
}
