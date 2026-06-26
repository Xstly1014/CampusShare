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
    // 未来实现：跳转到学校详情页
    console.log('Clicked school:', schoolId)
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 顶部搜索栏 */}
      <div className="sticky top-0 bg-white border-b border-gray-200 shadow-sm z-20">
        <div className="max-w-7xl mx-auto px-4 py-3">
          <SearchBar onSearch={setSearchKeyword} />
        </div>
      </div>

      {/* 主要内容区域 */}
      <div className="max-w-7xl mx-auto px-4 py-6">
        {/* 页面标题 */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900 mb-2">选择学校</h1>
          <p className="text-gray-600">
            浏览各高校的优质学习资源，共收录 {schoolsData.length} 所高校
          </p>
        </div>

        {/* 学校卡片网格 */}
        {filteredSchools.length > 0 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {filteredSchools.map((school: School) => (
              <SchoolCard
                key={school.id}
                school={school}
                onClick={() => handleSchoolClick(school.id)}
              />
            ))}
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-500 text-lg">未找到匹配的学校</p>
            <p className="text-gray-400 mt-2">请尝试其他搜索关键词</p>
          </div>
        )}
      </div>

      {/* 底部导航栏 */}
      <NavBar />
    </div>
  )
}