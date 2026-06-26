import { useState, useMemo } from 'react'
import SearchBar from '../components/home/SearchBar'
import SchoolCard from '../components/home/SchoolCard'
import NavBar from '../components/common/NavBar'
import schoolsData from '../data/schools.json'

interface School {
  id: string
  name: string
  logo: string
  resourceCount: number
}

export default function HomePage() {
  const [searchKeyword, setSearchKeyword] = useState('')

  const filteredSchools = useMemo(() => {
    if (!searchKeyword) {
      return schoolsData
    }
    return schoolsData.filter((school: School) =>
      school.name.toLowerCase().includes(searchKeyword.toLowerCase())
    )
  }, [searchKeyword])

  const handleSchoolClick = (schoolId: string) => {
    console.log('Clicked school:', schoolId)
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 顶部搜索栏 */}
      <div className="sticky top-0 bg-white border-b border-gray-100 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3">
          <SearchBar onSearch={setSearchKeyword} />
        </div>
      </div>

      {/* 主要内容区域 */}
      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* 页面标题 */}
        <div className="mb-6">
          <h1 className="text-xl font-bold text-gray-900 mb-1">选择学校</h1>
          <p className="text-sm text-gray-500">
            共收录 {schoolsData.length} 所高校，浏览优质学习资源
          </p>
        </div>

        {/* 学校卡片网格 - 一行四个 */}
        {filteredSchools.length > 0 ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
            {filteredSchools.map((school: School) => (
              <SchoolCard
                key={school.id}
                school={school}
                onClick={() => handleSchoolClick(school.id)}
              />
            ))}
          </div>
        ) : (
          <div className="text-center py-16">
            <p className="text-gray-400 text-sm">未找到匹配的学校</p>
            <p className="text-gray-300 text-xs mt-1">请尝试其他搜索关键词</p>
          </div>
        )}
      </div>

      {/* 底部导航栏 */}
      <NavBar />
    </div>
  )
}
