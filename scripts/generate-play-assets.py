from __future__ import annotations

import base64
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "docs" / "assets"
ASSETS_EN = ASSETS / "en"
OUT = ROOT / "docs" / "google-play" / "assets"
OUT.mkdir(parents=True, exist_ok=True)


def png_data_uri(path: Path) -> str:
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


def svg_circle_image(data_uri: str, x: int, y: int, size: int, ring: bool = True) -> str:
    clip_id = f"clip-{x}-{y}-{size}"
    stroke = '<circle cx="{cx}" cy="{cy}" r="{r2}" fill="none" stroke="rgba(255,255,255,0.24)" stroke-width="4"/>'.format(
        cx=x + size // 2,
        cy=y + size // 2,
        r2=size // 2 - 2,
    ) if ring else ""
    return f"""
    <defs>
      <clipPath id="{clip_id}">
        <circle cx="{x + size / 2}" cy="{y + size / 2}" r="{size / 2}" />
      </clipPath>
    </defs>
    <image href="{data_uri}" x="{x}" y="{y}" width="{size}" height="{size}" clip-path="url(#{clip_id})" preserveAspectRatio="xMidYMid slice" />
    {stroke}
    """


def write(path: Path, content: str) -> None:
    path.write_text(content.strip() + "\n", encoding="utf-8")


def render_png(svg_path: Path, png_path: Path) -> None:
    subprocess.run(
        ["sips", "-s", "format", "png", str(svg_path), "--out", str(png_path)],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def generate_feature_graphic(locale: str) -> None:
    is_en = locale == "en-US"
    base_assets = ASSETS_EN if is_en else ASSETS
    home = png_data_uri(base_assets / "wearpod-home.png")
    player = png_data_uri(base_assets / "wearpod-player.png")
    queue = png_data_uri(base_assets / "wearpod-queue.png")
    eyebrow = "WEARPOD"
    title_1 = "Standalone podcast listening" if is_en else "独立播客体验"
    title_2 = "for Wear OS" if is_en else "专为 Wear OS 手表打造"
    body_1 = "QR-assisted import and export, offline listening," if is_en else "手机扫码导入导出、离线收听，"
    body_2 = "and controls designed for round watches." if is_en else "以及专为圆形表盘设计的播放控制。"
    cta = "Import on phone, play on watch" if is_en else "手机导入，手表独立播放"
    svg = f"""
    <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#0E0C14"/>
          <stop offset="100%" stop-color="#1A1222"/>
        </linearGradient>
        <linearGradient id="glass" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="rgba(255,255,255,0.10)"/>
          <stop offset="100%" stop-color="rgba(255,255,255,0.03)"/>
        </linearGradient>
        <filter id="blur-xl" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="36"/>
        </filter>
      </defs>

      <rect width="1024" height="500" rx="0" fill="url(#bg)"/>
      <circle cx="844" cy="124" r="124" fill="#FF7B5C" opacity="0.16" filter="url(#blur-xl)"/>
      <circle cx="760" cy="362" r="170" fill="#7C5FFF" opacity="0.10" filter="url(#blur-xl)"/>
      <rect x="36" y="36" width="952" height="428" rx="42" fill="url(#glass)" stroke="rgba(255,255,255,0.14)" stroke-width="2"/>

      <text x="80" y="92" fill="#FF7B5C" font-size="22" font-family="Arial, sans-serif" font-weight="700" letter-spacing="2">{eyebrow}</text>
      <text x="80" y="154" fill="#F6F2FA" font-size="46" font-family="Arial, sans-serif" font-weight="700">{title_1}</text>
      <text x="80" y="206" fill="#F6F2FA" font-size="46" font-family="Arial, sans-serif" font-weight="700">{title_2}</text>
      <text x="80" y="280" fill="#B7ACC1" font-size="22" font-family="Arial, sans-serif">{body_1}</text>
      <text x="80" y="312" fill="#B7ACC1" font-size="22" font-family="Arial, sans-serif">{body_2}</text>

      <rect x="80" y="360" width="250" height="56" rx="28" fill="#FF7B5C"/>
      <text x="112" y="394" fill="#141017" font-size="18" font-family="Arial, sans-serif" font-weight="700">{cta}</text>

      {svg_circle_image(home, 562, 260, 168)}
      {svg_circle_image(player, 706, 112, 250)}
      {svg_circle_image(queue, 864, 304, 124)}
    </svg>
    """
    svg_path = OUT / f"wearpod-feature-graphic.{locale}.svg"
    png_path = OUT / f"wearpod-feature-graphic.{locale}.png"
    write(svg_path, svg)
    render_png(svg_path, png_path)


def generate_play_icon() -> None:
    svg = """
    <svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#100D18"/>
          <stop offset="100%" stop-color="#1D1427"/>
        </linearGradient>
      </defs>
      <rect width="512" height="512" rx="120" fill="url(#bg)"/>
      <rect x="18" y="18" width="476" height="476" rx="120" fill="none" stroke="rgba(255,255,255,0.16)" stroke-width="4"/>
      <circle cx="256" cy="256" r="136" fill="#FF7B5C"/>
      <polygon points="228,192 228,320 334,256" fill="#16111A"/>
    </svg>
    """
    svg_path = OUT / "wearpod-play-icon-512.svg"
    png_path = OUT / "wearpod-play-icon-512.png"
    write(svg_path, svg)
    render_png(svg_path, png_path)


if __name__ == "__main__":
    generate_feature_graphic("en-US")
    generate_feature_graphic("zh-CN")
    generate_play_icon()
