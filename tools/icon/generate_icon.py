"""Generates Luma's launcher/notification icons as Android vector drawables, plus a PNG preview.

The mascot is a chubby, slightly tilted star with sleepy eyes and blush cheeks, floating in a
night sky. Run from the repo root:

    python tools/icon/generate_icon.py

Writes the drawables into app/src/main/res and a preview to tools/icon/preview.png.
"""

import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
PREVIEW = Path(__file__).resolve().parent / "preview.png"

TILT_DEG = -8.0
STAR = dict(cx=54.0, cy=55.0, outer_r=27.5, inner_r=16.5, outer_cut=9.4, outer_k=0.78,
            inner_cut=4.4, inner_k=0.62)
EYES = [(49.2, 52.6), (58.8, 52.6)]
EYE_RX, EYE_RY = 2.3, 3.4
EYE_SHINE_OFFSET, EYE_SHINE_R = (0.75, -1.3), 0.9
CHEEKS = [(44.6, 59.0), (63.8, 59.0)]
CHEEK_RX, CHEEK_RY = 2.9, 1.7

BG_STOPS = [(0.0, "#5A3AA6"), (1.0, "#160F38")]
BG_CENTER, BG_RADIUS = (54.0, 42.0), 70.0
GLOW_CENTER, GLOW_RADIUS = (54.0, 55.0), 36.0
GLOW_STOPS = [(0.0, "#80FFF6D0"), (0.45, "#40D9C9FF"), (1.0, "#00C9B8FF")]
STAR_STOPS = [(0.0, "#FFFFF8D6"), (0.5, "#FFFFD84D"), (1.0, "#FFF2A12B")]
STAR_GRAD_CENTER, STAR_GRAD_RADIUS = (50.5, 47.0), 33.0
EYE_COLOR = "#FF2B1F4D"
CHEEK_COLOR = "#8CFF8FA3"

# (x, y, size): four-point sparkles in the night-sky background, kept inside the visible circle.
SPARKLES = [(31.0, 36.0, 3.4), (77.0, 33.0, 2.8), (76.0, 76.0, 3.0), (31.0, 74.0, 2.2),
            (64.0, 23.0, 1.8)]
DOTS = [(41.0, 25.0, 0.8), (85.0, 54.0, 0.75), (23.0, 56.0, 0.75), (65.0, 84.0, 0.8),
        (45.0, 87.0, 0.6), (13.0, 30.0, 0.6), (95.0, 80.0, 0.6)]


def fmt(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def star_segments(cx, cy, outer_r, inner_r, outer_cut, outer_k, inner_cut, inner_k):
    """Returns the start point and a list of (line_to, c1, c2, end) cubic segments."""
    verts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = outer_r if i % 2 == 0 else inner_r
        verts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))

    def toward(a, b, d):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy)
        return a[0] + dx / length * d, a[1] + dy / length * d

    def lerp(a, b, t):
        return a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t

    segs = []
    for i, v in enumerate(verts):
        prev, nxt = verts[i - 1], verts[(i + 1) % 10]
        cut, k = (outer_cut, outer_k) if i % 2 == 0 else (inner_cut, inner_k)
        p_in, p_out = toward(v, prev, cut), toward(v, nxt, cut)
        segs.append((p_in, lerp(p_in, v, k), lerp(p_out, v, k), p_out))
    return segs


def star_path_data(**kw) -> str:
    segs = star_segments(**kw)
    out = []
    for i, (p_in, c1, c2, p_out) in enumerate(segs):
        out.append(("M" if i == 0 else "L") + f"{fmt(p_in[0])},{fmt(p_in[1])}")
        out.append(f"C{fmt(c1[0])},{fmt(c1[1])} {fmt(c2[0])},{fmt(c2[1])} {fmt(p_out[0])},{fmt(p_out[1])}")
    return " ".join(out) + " Z"


def star_polygon(steps=24, **kw):
    pts = []
    for p_in, c1, c2, p_out in star_segments(**kw):
        pts.append(p_in)
        for s in range(1, steps + 1):
            t = s / steps
            mt = 1 - t
            pts.append((
                mt ** 3 * p_in[0] + 3 * mt * mt * t * c1[0] + 3 * mt * t * t * c2[0] + t ** 3 * p_out[0],
                mt ** 3 * p_in[1] + 3 * mt * mt * t * c1[1] + 3 * mt * t * t * c2[1] + t ** 3 * p_out[1],
            ))
    return pts


def ellipse_path(cx, cy, rx, ry) -> str:
    return (f"M{fmt(cx - rx)},{fmt(cy)} a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0 "
            f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0 Z")


def sparkle_path(x, y, s) -> str:
    """A four-point sparkle with concave sides."""
    w = s * 0.22
    return (f"M{fmt(x)},{fmt(y - s)} Q{fmt(x + w)},{fmt(y - w)} {fmt(x + s)},{fmt(y)} "
            f"Q{fmt(x + w)},{fmt(y + w)} {fmt(x)},{fmt(y + s)} "
            f"Q{fmt(x - w)},{fmt(y + w)} {fmt(x - s)},{fmt(y)} "
            f"Q{fmt(x - w)},{fmt(y - w)} {fmt(x)},{fmt(y - s)} Z")


def sparkle_polygon(x, y, s, steps=10):
    w = s * 0.22
    corners = [(x, y - s), (x + s, y), (x, y + s), (x - s, y), (x, y - s)]
    ctrls = [(x + w, y - w), (x + w, y + w), (x - w, y + w), (x - w, y - w)]
    pts = []
    for (a, b), c in zip(zip(corners, corners[1:]), ctrls):
        for i in range(steps):
            t = i / steps
            mt = 1 - t
            pts.append((mt * mt * a[0] + 2 * mt * t * c[0] + t * t * b[0],
                        mt * mt * a[1] + 2 * mt * t * c[1] + t * t * b[1]))
    return pts


def gradient_xml(kind, stops, indent, **attrs) -> str:
    pad = " " * indent
    attr_str = " ".join(f'android:{k}="{fmt(v) if isinstance(v, float) else v}"' for k, v in attrs.items())
    items = "\n".join(f'{pad}        <item android:offset="{fmt(o)}" android:color="{c}" />' for o, c in stops)
    return (f'{pad}<aapt:attr name="android:fillColor">\n'
            f'{pad}    <gradient android:type="{kind}" {attr_str}>\n{items}\n'
            f'{pad}    </gradient>\n{pad}</aapt:attr>')


VECTOR_HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
                 '<!-- Generated by tools/icon/generate_icon.py; edit the script, not this file. -->\n')


def write_background():
    sparkles = "\n".join(
        f'    <path android:fillColor="#E6FFF4D0" android:pathData="{sparkle_path(x, y, s)}" />'
        for x, y, s in SPARKLES)
    dots = "\n".join(
        f'    <path android:fillColor="#B3FFFFFF" android:pathData="{ellipse_path(x, y, r, r)}" />'
        for x, y, r in DOTS)
    xml = (VECTOR_HEADER +
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           '    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">\n'
           + gradient_xml("radial", BG_STOPS, 8, centerX=BG_CENTER[0], centerY=BG_CENTER[1],
                          gradientRadius=BG_RADIUS) + "\n"
           '    </path>\n' + sparkles + "\n" + dots + "\n</vector>\n")
    (RES / "drawable" / "ic_launcher_background.xml").write_text(xml, encoding="utf-8")


def face_paths(indent=8) -> str:
    pad = " " * indent
    lines = []
    for cx, cy in CHEEKS:
        lines.append(f'{pad}<path android:fillColor="{CHEEK_COLOR}" '
                     f'android:pathData="{ellipse_path(cx, cy, CHEEK_RX, CHEEK_RY)}" />')
    for cx, cy in EYES:
        lines.append(f'{pad}<path android:fillColor="{EYE_COLOR}" '
                     f'android:pathData="{ellipse_path(cx, cy, EYE_RX, EYE_RY)}" />')
        sx, sy = cx + EYE_SHINE_OFFSET[0], cy + EYE_SHINE_OFFSET[1]
        lines.append(f'{pad}<path android:fillColor="#FFFFFFFF" '
                     f'android:pathData="{ellipse_path(sx, sy, EYE_SHINE_R, EYE_SHINE_R)}" />')
    return "\n".join(lines)


def write_foreground():
    xml = (VECTOR_HEADER +
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           f'    <path android:pathData="{ellipse_path(GLOW_CENTER[0], GLOW_CENTER[1], GLOW_RADIUS, GLOW_RADIUS)}">\n'
           + gradient_xml("radial", GLOW_STOPS, 8, centerX=GLOW_CENTER[0], centerY=GLOW_CENTER[1],
                          gradientRadius=GLOW_RADIUS) + "\n"
           '    </path>\n'
           f'    <group android:rotation="{fmt(TILT_DEG)}" android:pivotX="{fmt(STAR["cx"])}" '
           f'android:pivotY="{fmt(STAR["cy"])}">\n'
           f'        <path android:pathData="{star_path_data(**STAR)}">\n'
           + gradient_xml("radial", STAR_STOPS, 12, centerX=STAR_GRAD_CENTER[0],
                          centerY=STAR_GRAD_CENTER[1], gradientRadius=STAR_GRAD_RADIUS) + "\n"
           '        </path>\n' + face_paths() + "\n"
           '    </group>\n</vector>\n')
    (RES / "drawable" / "ic_launcher_foreground.xml").write_text(xml, encoding="utf-8")


def silhouette_path(scale=1.0, dx=0.0, dy=0.0) -> str:
    """Star with the eyes cut out (evenOdd), optionally rescaled into another viewport."""
    star = {k: (v * scale if k not in ("outer_k", "inner_k") else v) for k, v in STAR.items()}
    star["cx"] += dx
    star["cy"] += dy
    eyes = " ".join(
        ellipse_path(cx * scale + dx, cy * scale + dy, EYE_RX * scale * 1.1, EYE_RY * scale * 1.1)
        for cx, cy in EYES)
    return star_path_data(**star) + " " + eyes


def write_monochrome():
    xml = (VECTOR_HEADER +
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           f'    <group android:rotation="{fmt(TILT_DEG)}" android:pivotX="{fmt(STAR["cx"])}" '
           f'android:pivotY="{fmt(STAR["cy"])}">\n'
           f'        <path android:fillColor="#FF000000" android:fillType="evenOdd"\n'
           f'            android:pathData="{silhouette_path()}" />\n'
           '    </group>\n</vector>\n')
    (RES / "drawable" / "ic_launcher_monochrome.xml").write_text(xml, encoding="utf-8")


def write_notification_icon():
    # Fit the 108-unit star (about 62 units across) into a 24-unit viewport with a little margin.
    scale = 22.0 / 62.0
    dx, dy = 12.0 - STAR["cx"] * scale, 12.4 - STAR["cy"] * scale
    xml = (VECTOR_HEADER +
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="24dp" android:height="24dp"\n'
           '    android:viewportWidth="24" android:viewportHeight="24"\n'
           '    android:tint="?android:attr/colorControlNormal">\n'
           f'    <group android:rotation="{fmt(TILT_DEG)}" android:pivotX="12" android:pivotY="12.4">\n'
           f'        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"\n'
           f'            android:pathData="{silhouette_path(scale, dx, dy)}" />\n'
           '    </group>\n</vector>\n')
    (RES / "drawable" / "ic_stat_luma.xml").write_text(xml, encoding="utf-8")


# --- PNG preview -------------------------------------------------------------------------------

def hex_rgba(c):
    c = c.lstrip("#")
    if len(c) == 6:
        c = "FF" + c
    a, r, g, b = (int(c[i:i + 2], 16) for i in range(0, 8, 2))
    return r, g, b, a


def radial_image(size, scale, center, radius, stops):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    cols = [(o, hex_rgba(c)) for o, c in stops]
    for y in range(size):
        for x in range(size):
            d = math.hypot(x / scale - center[0], y / scale - center[1]) / radius
            d = min(max(d, 0.0), 1.0)
            for (o0, c0), (o1, c1) in zip(cols, cols[1:]):
                if d <= o1:
                    t = 0 if o1 == o0 else (d - o0) / (o1 - o0)
                    px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(c0, c1))
                    break
            else:
                px[x, y] = cols[-1][1]
    return img


def rotate_pts(pts, deg, pivot):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return [(pivot[0] + (x - pivot[0]) * ca - (y - pivot[1]) * sa,
             pivot[1] + (x - pivot[0]) * sa + (y - pivot[1]) * ca) for x, y in pts]


def ellipse_pts(cx, cy, rx, ry, n=48):
    return [(cx + rx * math.cos(2 * math.pi * i / n), cy + ry * math.sin(2 * math.pi * i / n))
            for i in range(n)]


def fill_shape(canvas, pts, scale, fill):
    """Composites a solid fill or a gradient image through the polygon mask."""
    mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(mask).polygon([(x * scale, y * scale) for x, y in pts], fill=255)
    layer = fill if isinstance(fill, Image.Image) else Image.new("RGBA", canvas.size, hex_rgba(fill))
    alpha = ImageChops.multiply(layer.getchannel("A"), mask)
    layer = layer.copy()
    layer.putalpha(alpha)
    canvas.alpha_composite(layer)


def render_preview():
    scale = 4
    size = 108 * scale
    pivot = (STAR["cx"], STAR["cy"])
    img = radial_image(size, scale, BG_CENTER, BG_RADIUS, BG_STOPS)
    for x, y, s in SPARKLES:
        fill_shape(img, sparkle_polygon(x, y, s), scale, "#E6FFF4D0")
    for x, y, r in DOTS:
        fill_shape(img, ellipse_pts(x, y, r, r), scale, "#B3FFFFFF")
    img.alpha_composite(radial_image(size, scale, GLOW_CENTER, GLOW_RADIUS, GLOW_STOPS))
    star_grad = radial_image(size, scale, rotate_pts([STAR_GRAD_CENTER], TILT_DEG, pivot)[0],
                             STAR_GRAD_RADIUS, STAR_STOPS)
    fill_shape(img, rotate_pts(star_polygon(**STAR), TILT_DEG, pivot), scale, star_grad)
    for cx, cy in CHEEKS:
        fill_shape(img, rotate_pts(ellipse_pts(cx, cy, CHEEK_RX, CHEEK_RY), TILT_DEG, pivot), scale,
                   CHEEK_COLOR)
    for cx, cy in EYES:
        fill_shape(img, rotate_pts(ellipse_pts(cx, cy, EYE_RX, EYE_RY), TILT_DEG, pivot), scale,
                   EYE_COLOR)
        sx, sy = cx + EYE_SHINE_OFFSET[0], cy + EYE_SHINE_OFFSET[1]
        fill_shape(img, rotate_pts(ellipse_pts(sx, sy, EYE_SHINE_R, EYE_SHINE_R), TILT_DEG, pivot),
                   scale, "#FFFFFFFF")

    # Launchers show the centre 72dp of the 108dp layers, usually through a circle mask.
    visible = 72 * scale
    off = (size - visible) // 2
    icon = img.crop((off, off, off + visible, off + visible))
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, visible - 1, visible - 1), fill=255)
    round_icon = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    round_icon.paste(icon, mask=mask)

    sheet = Image.new("RGBA", (size + visible + 3 * 24, size + 48), (245, 245, 245, 255))
    sheet.alpha_composite(img, (24, 24))
    sheet.alpha_composite(round_icon, (size + 48, 24 + (size - visible) // 2))
    sheet.save(PREVIEW)


if __name__ == "__main__":
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    write_background()
    write_foreground()
    write_monochrome()
    write_notification_icon()
    render_preview()
    print(f"Wrote icons to {RES / 'drawable'} and preview to {PREVIEW}")
