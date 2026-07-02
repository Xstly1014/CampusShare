import { useRef, useState, ReactNode } from 'react'
import { Trash2, FolderInput } from 'lucide-react'

interface SwipeToDeleteProps {
  onDelete: () => void
  onMove?: () => void
  moveLabel?: string
  children: ReactNode
  isOpen: boolean
  onOpenChange: (open: boolean) => void
}

export default function SwipeToDelete({ onDelete, onMove, moveLabel = '分类', children, isOpen, onOpenChange }: SwipeToDeleteProps) {
  const startXRef = useRef(0)
  const draggingRef = useRef(false)
  const movedRef = useRef(false)
  const [dragX, setDragX] = useState(0)

  const ACTION_WIDTH = onMove ? 160 : 72
  const THRESHOLD = onMove ? 64 : 32

  const handlePointerDown = (e: React.PointerEvent) => {
    startXRef.current = e.clientX
    draggingRef.current = true
    movedRef.current = false
    setDragX(isOpen ? -ACTION_WIDTH : 0)
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!draggingRef.current) return
    const delta = e.clientX - startXRef.current
    if (Math.abs(delta) > 5) movedRef.current = true
    const base = isOpen ? -ACTION_WIDTH : 0
    let next = base + delta
    if (next > 0) next = 0
    if (next < -ACTION_WIDTH) next = -ACTION_WIDTH
    setDragX(next)
  }

  const handlePointerUp = () => {
    if (!draggingRef.current) return
    draggingRef.current = false
    if (dragX <= -THRESHOLD) {
      setDragX(-ACTION_WIDTH)
      onOpenChange(true)
    } else {
      setDragX(0)
      onOpenChange(false)
    }
  }

  const handleClickCapture = (e: React.MouseEvent) => {
    if (movedRef.current) {
      e.preventDefault()
      e.stopPropagation()
      movedRef.current = false
    }
  }

  const translateX = draggingRef.current ? dragX : (isOpen ? -ACTION_WIDTH : 0)

  return (
    <div className="relative overflow-hidden rounded-xl">
      <div className="absolute right-0 top-0 bottom-0 flex">
        {onMove && (
          <button
            onClick={onMove}
            className="flex items-center justify-center bg-blue-500 text-white"
            style={{ width: 80 }}
          >
            <div className="flex flex-col items-center gap-1">
              <FolderInput className="w-4 h-4" />
              <span className="text-[11px]">{moveLabel}</span>
            </div>
          </button>
        )}
        <button
          onClick={onDelete}
          className="flex items-center justify-center bg-red-500 text-white"
          style={{ width: onMove ? 80 : 72 }}
        >
          <div className="flex flex-col items-center gap-1">
            <Trash2 className="w-4 h-4" />
            <span className="text-[11px]">删除</span>
          </div>
        </button>
      </div>
      <div
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onClickCapture={handleClickCapture}
        style={{
          transform: `translateX(${translateX}px)`,
          transition: draggingRef.current ? 'none' : 'transform 0.25s ease',
          touchAction: 'pan-y',
        }}
        className="relative bg-white"
      >
        {children}
      </div>
    </div>
  )
}
