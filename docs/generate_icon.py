"""生成自律助手原创图标：深蓝圆角方块 + 白色锁形 + 进度弧线"""
from PIL import Image, ImageDraw

def draw_icon(size):
    # 4x 超采样抗锯齿
    s = size * 4
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 深蓝圆角方块背景
    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill='#1565C0')

    cx, cy = s / 2, s / 2

    # 白色进度弧线（270度，顶部开口）
    ring_r = s * 0.32
    ring_w = int(s * 0.045)
    bbox = [cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r]
    d.arc(bbox, start=-230, end=50, fill='#FFFFFF', width=ring_w)

    # 锁体（白色圆角矩形）
    body_w, body_h = s * 0.34, s * 0.24
    body = [cx - body_w / 2, cy - body_h * 0.1, cx + body_w / 2, cy - body_h * 0.1 + body_h]
    d.rounded_rectangle(body, radius=int(s * 0.04), fill='#FFFFFF')

    # 锁梁（白色圆环描边）
    shackle_r = s * 0.11
    shackle_w = int(s * 0.05)
    shackle_top = cy - body_h * 0.1 - shackle_r * 1.6
    d.arc([cx - shackle_r, shackle_top, cx + shackle_r, shackle_top + shackle_r * 2],
          start=180, end=360, fill='#FFFFFF', width=shackle_w)
    # 锁梁竖腿
    leg_y1 = shackle_top + shackle_r
    leg_y2 = cy - body_h * 0.1 + 2
    d.line([cx - shackle_r, leg_y1, cx - shackle_r, leg_y2], fill='#FFFFFF', width=shackle_w)
    d.line([cx + shackle_r, leg_y1, cx + shackle_r, leg_y2], fill='#FFFFFF', width=shackle_w)

    # 钥匙孔（深蓝圆点 + 竖线）
    kh_r = s * 0.035
    kh_cy = cy + body_h * 0.25
    d.ellipse([cx - kh_r, kh_cy - kh_r, cx + kh_r, kh_cy + kh_r], fill='#1565C0')
    d.line([cx, kh_cy, cx, kh_cy + s * 0.06], fill='#1565C0', width=int(s * 0.035))

    return img.resize((size, size), Image.LANCZOS)

for px, density in [(48, 'mdpi'), (72, 'hdpi'), (96, 'xhdpi'), (144, 'xxhdpi'), (192, 'xxxhdpi'), (512, 'playstore')]:
    icon = draw_icon(px)
    if density == 'playstore':
        icon.save('/tmp/icon_gen/ic_launcher-playstore.png')
    else:
        icon.save(f'flutter_app/android/app/src/main/res/mipmap-{density}/ic_launcher.png')  # 从仓库根目录运行
        print(f'mipmap-{density}: {px}px')
print('done')
