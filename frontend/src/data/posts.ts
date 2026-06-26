export type PostType = 'resource' | 'discussion' | 'note'

export interface Post {
  id: string
  schoolId: string
  type: PostType
  title: string
  author: {
    id: string
    username: string
    avatar?: string
  }
  createdAt: string
  stars: number
  comments: number
  views: number
  isStarred: boolean
  // 资源贴特有
  fileUrl?: string
  fileType?: string
  fileSize?: string
  description?: string
  // 讨论贴特有
  content?: string
}

export interface Comment {
  id: string
  postId: string
  author: {
    id: string
    username: string
    avatar: string
  }
  content: string
  createdAt: string
  likes: number
  isLiked: boolean
  replyTo?: {
    id: string
    username: string
  }
}

export const commentsData: Comment[] = [
  {
    id: 'c1',
    postId: 'r1',
    author: { id: 'u9', username: '数学系学渣', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Jack' },
    content: '感谢分享！刚好期末要用到，太及时了',
    createdAt: '2024-12-16',
    likes: 12,
    isLiked: false,
  },
  {
    id: 'c2',
    postId: 'r1',
    author: { id: 'u10', username: '熬夜复习人', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Kiki' },
    content: '第三章的例题和老师讲的不一样啊，是版本问题吗？',
    createdAt: '2024-12-16',
    likes: 3,
    isLiked: false,
    replyTo: { id: 'u1', username: '数学小王子' },
  },
  {
    id: 'c3',
    postId: 'r1',
    author: { id: 'u1', username: '数学小王子', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix' },
    content: '是用的最新版教材整理的，可能和之前的版本有差异，以老师PPT为准哈',
    createdAt: '2024-12-17',
    likes: 8,
    isLiked: false,
  },
  {
    id: 'c4',
    postId: 'd1',
    author: { id: 'u11', username: '选课困难户', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Lily' },
    content: '同问！有没有推荐的水课？想选几门轻松点的',
    createdAt: '2024-12-18',
    likes: 23,
    isLiked: false,
  },
  {
    id: 'c5',
    postId: 'd1',
    author: { id: 'u12', username: '绩点4.0', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Mike' },
    content: '推荐张老师的西方哲学史，讲课很有意思，给分也不错',
    createdAt: '2024-12-19',
    likes: 56,
    isLiked: true,
  },
  {
    id: 'c6',
    postId: 'd1',
    author: { id: 'u13', username: '计算机新生', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Nina' },
    content: '避雷李老师的计算机导论，作业巨多，考试还难',
    createdAt: '2024-12-19',
    likes: 34,
    isLiked: false,
  },
]

export const postsData: Post[] = [
  {
    id: 'r1',
    schoolId: '1',
    type: 'resource',
    title: '高等数学期末复习重点整理（2024秋）',
    author: { id: 'u1', username: '数学小王子', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix' },
    createdAt: '2024-12-15',
    stars: 328,
    comments: 45,
    views: 2103,
    isStarred: false,
    fileUrl: '#',
    fileType: 'pdf',
    fileSize: '2.3 MB',
    description: '根据授课老师PPT和教材整理，涵盖了期末考试的全部重点内容，包括例题和答题技巧。',
  },
  {
    id: 'r2',
    schoolId: '1',
    type: 'resource',
    title: '线性代数知识点总结（思维导图版）',
    author: { id: 'u2', username: '学霸小李', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka' },
    createdAt: '2024-12-10',
    stars: 256,
    comments: 32,
    views: 1820,
    isStarred: true,
    fileUrl: '#',
    fileType: 'md',
    fileSize: '1.1 MB',
    description: '用思维导图的形式整理了线性代数的核心知识点，方便理解和记忆。',
  },
  {
    id: 'd1',
    schoolId: '1',
    type: 'discussion',
    title: '2025年春季学期选课交流',
    author: { id: 'u3', username: '校园生活家', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Bella' },
    createdAt: '2024-12-18',
    stars: 89,
    comments: 127,
    views: 3560,
    isStarred: false,
    content: '欢迎大家来讨论选课经验！有没有学长学姐推荐的好课？或者哪些课需要避雷的？',
  },
  {
    id: 'r3',
    schoolId: '1',
    type: 'resource',
    title: '计算机组成原理实验报告模板',
    author: { id: 'u4', username: '代码农名', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Charlie' },
    createdAt: '2024-12-08',
    stars: 178,
    comments: 21,
    views: 956,
    isStarred: false,
    fileUrl: '#',
    fileType: 'docx',
    fileSize: '0.5 MB',
    description: '包含实验要求、格式规范和评分标准，可直接填写使用。',
  },
  {
    id: 'd2',
    schoolId: '1',
    type: 'discussion',
    title: '期末考试周图书馆占座攻略',
    author: { id: 'u5', username: '早起鸟', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Daisy' },
    createdAt: '2024-12-17',
    stars: 445,
    comments: 89,
    views: 5200,
    isStarred: true,
    content: '每年考试周图书馆都人满为患，分享一些占座的小技巧和替代学习地点。',
  },
  {
    id: 'r4',
    schoolId: '1',
    type: 'resource',
    title: '2024-2025学年秋季课程PPT汇总',
    author: { id: 'u6', username: '资料搜集员', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Eve' },
    createdAt: '2024-12-05',
    stars: 612,
    comments: 78,
    views: 4200,
    isStarred: false,
    fileUrl: '#',
    fileType: 'zip',
    fileSize: '45.6 MB',
    description: '整理了本学期所有专业课的课件PPT，共15门课程，非常全面。',
  },
  {
    id: 'd3',
    schoolId: '1',
    type: 'discussion',
    title: '校园美食推荐第二弹',
    author: { id: 'u7', username: '吃货小分队', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Fiona' },
    createdAt: '2024-12-16',
    stars: 567,
    comments: 203,
    views: 8900,
    isStarred: false,
    content: '上一期的美食推荐反响很好，这次带来第二弹！新增了5家食堂和周边小店的测评。',
  },
  {
    id: 'r5',
    schoolId: '1',
    type: 'resource',
    title: '英语四六级高频词汇表（按频率排序）',
    author: { id: 'u8', username: '英语达人', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=George' },
    createdAt: '2024-12-12',
    stars: 892,
    comments: 156,
    views: 7800,
    isStarred: false,
    fileUrl: '#',
    fileType: 'pdf',
    fileSize: '0.8 MB',
    description: '基于近5年真题词频分析，高频词汇优先记忆，含例句和词根解析。',
  },
  {
    id: 'r6',
    schoolId: '2',
    type: 'resource',
    title: '微积分B课程全套笔记',
    author: { id: 'u9', username: '清华小透明', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Hannah' },
    createdAt: '2024-12-14',
    stars: 456,
    comments: 67,
    views: 3200,
    isStarred: false,
    fileUrl: '#',
    fileType: 'pdf',
    fileSize: '8.2 MB',
    description: '一学期完整的课堂笔记，字迹清晰，配有图示和例题。',
  },
  {
    id: 'd4',
    schoolId: '2',
    type: 'discussion',
    title: '清华紫操夜聊',
    author: { id: 'u10', username: '紫操常客', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Ivan' },
    createdAt: '2024-12-19',
    stars: 234,
    comments: 89,
    views: 4100,
    isStarred: false,
    content: '期末压力大，来紫操聊聊天吧！有什么烦心事或者开心的事都可以分享。',
  },
]
