import { motion, type HTMLMotionProps } from 'framer-motion'

type Props = HTMLMotionProps<'div'> & { delay?: number }

/** 스크롤 진입 시 24px 아래에서 올라오며 나타나는 래퍼. 1회만 발동. */
export default function Reveal({ children, delay = 0, ...rest }: Props) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '0px 0px -15% 0px' }}
      transition={{ duration: 0.9, ease: [0.2, 0.7, 0.2, 1], delay }}
      {...rest}
    >
      {children}
    </motion.div>
  )
}
