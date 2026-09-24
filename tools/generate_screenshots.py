#!/usr/bin/env python3
"""Generates clean, native-styled documentation screenshots matching the app UI."""

import os
from PIL import Image, ImageDraw, ImageFont

SCREEN_WIDTH = 540
SCREEN_HEIGHT = 1200

BG_COLOR = (248, 249, 250) # Light pastel background
CARD_BG = (255, 255, 255)
PRIMARY_TEXT = (17, 24, 39)
SECONDARY_TEXT = (107, 114, 128)
ACCENT_BLUE = (26, 155, 240)
ACCENT_RED = (239, 68, 68)
BORDER_COLOR = (229, 231, 235)
PASTEL_PEACH = (255, 237, 213)
PASTEL_MINT = (209, 250, 229)
PASTEL_LAVENDER = (237, 233, 254)

def get_font(size, bold=False):
    # Try system fonts on macOS
    font_paths = [
        "/System/Library/Fonts/SFPro.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/Library/Fonts/Arial.ttf"
    ]
    for p in font_paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()

def draw_status_bar(draw):
    font = get_font(14, bold=True)
    draw.text((36, 16), "9:41", fill=PRIMARY_TEXT, font=font)
    # Battery / wifi indicators
    draw.rectangle([SCREEN_WIDTH - 64, 18, SCREEN_WIDTH - 40, 30], outline=PRIMARY_TEXT, width=1)
    draw.rectangle([SCREEN_WIDTH - 62, 20, SCREEN_WIDTH - 46, 28], fill=PRIMARY_TEXT)
    draw.rectangle([SCREEN_WIDTH - 39, 21, SCREEN_WIDTH - 38, 27], fill=PRIMARY_TEXT)

def draw_top_bar(draw, title):
    # Back button circle
    draw.ellipse([24, 52, 64, 92], fill=(255, 255, 255), outline=BORDER_COLOR, width=1)
    # Back arrow (<)
    draw.line([48, 64, 38, 72], fill=PRIMARY_TEXT, width=2)
    draw.line([38, 72, 48, 80], fill=PRIMARY_TEXT, width=2)

    font_kicker = get_font(12, bold=True)
    font_title = get_font(24, bold=True)
    draw.text((32, 116), "MIC TO SPEAKER", fill=ACCENT_BLUE, font=font_kicker)
    draw.text((32, 136), title, fill=PRIMARY_TEXT, font=font_title)

def create_base_canvas():
    img = Image.new("RGB", (SCREEN_WIDTH, SCREEN_HEIGHT), BG_COLOR)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw)
    return img, draw

def render_live_microphone(out_path):
    img, draw = create_base_canvas()
    draw_top_bar(draw, "Live Microphone")

    # Center Hero stage (circular card)
    center_x = SCREEN_WIDTH // 2
    hero_y = 360
    draw.ellipse([center_x - 110, hero_y - 110, center_x + 110, hero_y + 110], fill=PASTEL_MINT, outline=BORDER_COLOR)
    draw.ellipse([center_x - 85, hero_y - 85, center_x + 85, hero_y + 85], fill=(255, 255, 255))

    # Paste hero icon if available
    icon_path = "app/src/main/res/drawable-nodpi/ic_home_live_mic.png"
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert("RGBA").resize((110, 110))
        img.paste(icon, (center_x - 55, hero_y - 55), icon)

    # Status / action button
    btn_y = 520
    draw.rounded_rectangle([center_x - 90, btn_y, center_x + 90, btn_y + 54], radius=27, fill=ACCENT_BLUE)
    font_btn = get_font(16, bold=True)
    draw.text((center_x - 30, btn_y + 17), "START", fill=(255, 255, 255), font=font_btn)

    font_sub = get_font(14)
    draw.text((center_x - 70, btn_y + 68), "Microphone off · Tap to start", fill=SECONDARY_TEXT, font=font_sub)

    # Gain card
    card_y = 660
    draw.rounded_rectangle([24, card_y, SCREEN_WIDTH - 24, card_y + 110], radius=16, fill=CARD_BG, outline=BORDER_COLOR)
    draw.text((44, card_y + 18), "Monitoring Gain", fill=PRIMARY_TEXT, font=get_font(15, bold=True))
    draw.text((SCREEN_WIDTH - 84, card_y + 18), "80%", fill=ACCENT_BLUE, font=get_font(15, bold=True))
    # Slider track
    draw.rounded_rectangle([44, card_y + 56, SCREEN_WIDTH - 44, card_y + 64], radius=4, fill=(229, 231, 235))
    draw.rounded_rectangle([44, card_y + 56, int(44 + (SCREEN_WIDTH - 88) * 0.8), card_y + 64], radius=4, fill=ACCENT_BLUE)
    # Slider thumb
    thumb_x = int(44 + (SCREEN_WIDTH - 88) * 0.8)
    draw.ellipse([thumb_x - 10, card_y + 50, thumb_x + 10, card_y + 70], fill=ACCENT_BLUE)

    # Safety note card
    note_y = 800
    draw.rounded_rectangle([24, note_y, SCREEN_WIDTH - 24, note_y + 120], radius=16, fill=PASTEL_PEACH, outline=BORDER_COLOR)
    draw.text((44, note_y + 18), "Prevent loud feedback", fill=PRIMARY_TEXT, font=get_font(14, bold=True))
    draw.text((44, note_y + 44), "Start with low speaker volume. Keep the", fill=SECONDARY_TEXT, font=get_font(12))
    draw.text((44, note_y + 64), "microphone away from speakers; headphones", fill=SECONDARY_TEXT, font=get_font(12))
    draw.text((44, note_y + 84), "are safer.", fill=SECONDARY_TEXT, font=get_font(12))

    img.save(out_path, "PNG", optimize=True)

def render_hold_to_speak(out_path):
    img, draw = create_base_canvas()
    draw_top_bar(draw, "Hold to Speak")

    center_x = SCREEN_WIDTH // 2
    hero_y = 400
    # Large circular hold button
    draw.ellipse([center_x - 120, hero_y - 120, center_x + 120, hero_y + 120], fill=PASTEL_PEACH, outline=BORDER_COLOR)
    draw.ellipse([center_x - 95, hero_y - 95, center_x + 95, hero_y + 95], fill=ACCENT_RED)

    icon_path = "app/src/main/res/drawable-nodpi/ic_home_hold_speaker.png"
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert("RGBA").resize((100, 100))
        img.paste(icon, (center_x - 50, hero_y - 50), icon)

    font_btn = get_font(16, bold=True)
    draw.text((center_x - 62, hero_y + 145), "HOLD TO SPEAK", fill=PRIMARY_TEXT, font=font_btn)
    draw.text((center_x - 90, hero_y + 175), "Release to save, slide to cancel", fill=SECONDARY_TEXT, font=get_font(13))

    # Info card
    card_y = 680
    draw.rounded_rectangle([24, card_y, SCREEN_WIDTH - 24, card_y + 140], radius=16, fill=CARD_BG, outline=BORDER_COLOR)
    draw.text((44, card_y + 20), "How it works", fill=PRIMARY_TEXT, font=get_font(15, bold=True))
    draw.text((44, card_y + 50), "• Press and hold the red button to record.", fill=SECONDARY_TEXT, font=get_font(13))
    draw.text((44, card_y + 75), "• Release when finished to save your clip.", fill=SECONDARY_TEXT, font=get_font(13))
    draw.text((44, card_y + 100), "• Slide finger away to cancel without saving.", fill=SECONDARY_TEXT, font=get_font(13))

    img.save(out_path, "PNG", optimize=True)

def render_recorder(out_path):
    img, draw = create_base_canvas()
    draw_top_bar(draw, "Record Audio")

    center_x = SCREEN_WIDTH // 2
    hero_y = 380
    draw.ellipse([center_x - 110, hero_y - 110, center_x + 110, hero_y + 110], fill=PASTEL_LAVENDER, outline=BORDER_COLOR)
    draw.ellipse([center_x - 85, hero_y - 85, center_x + 85, hero_y + 85], fill=(255, 255, 255))

    icon_path = "app/src/main/res/drawable-nodpi/ic_home_record_mic.png"
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert("RGBA").resize((110, 110))
        img.paste(icon, (center_x - 55, hero_y - 55), icon)

    # Timer display
    font_timer = get_font(32, bold=True)
    draw.text((center_x - 65, hero_y + 130), "00:00:00", fill=PRIMARY_TEXT, font=font_timer)

    # Record button
    btn_y = 590
    draw.rounded_rectangle([center_x - 90, btn_y, center_x + 90, btn_y + 54], radius=27, fill=ACCENT_RED)
    draw.text((center_x - 36, btn_y + 17), "RECORD", fill=(255, 255, 255), font=get_font(16, bold=True))

    card_y = 700
    draw.rounded_rectangle([24, card_y, SCREEN_WIDTH - 24, card_y + 120], radius=16, fill=CARD_BG, outline=BORDER_COLOR)
    draw.text((44, card_y + 20), "Recording Format", fill=PRIMARY_TEXT, font=get_font(15, bold=True))
    draw.text((44, card_y + 50), "• High-efficiency AAC in MPEG-4 (.m4a)", fill=SECONDARY_TEXT, font=get_font(13))
    draw.text((44, card_y + 75), "• Safe recovery: crashes never corrupt audio", fill=SECONDARY_TEXT, font=get_font(13))

    img.save(out_path, "PNG", optimize=True)

def render_settings(out_path):
    img, draw = create_base_canvas()
    draw_top_bar(draw, "Settings")

    # Gear card
    card_y = 200
    draw.rounded_rectangle([24, card_y, SCREEN_WIDTH - 24, card_y + 100], radius=16, fill=PASTEL_LAVENDER, outline=BORDER_COLOR)
    icon_path = "app/src/main/res/drawable/art_3d_gear.png"
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert("RGBA").resize((60, 60))
        img.paste(icon, (40, card_y + 20), icon)
    draw.text((120, card_y + 26), "Preferences", fill=PRIMARY_TEXT, font=get_font(18, bold=True))
    draw.text((120, card_y + 52), "Customize your audio experience", fill=SECONDARY_TEXT, font=get_font(13))

    # Menu items
    items = [
        ("Audio Settings", "Default gain, format & feedback sensitivity"),
        ("Ad Privacy Choices", "Manage consent and ad preferences"),
        ("Storage & Files", "Manage recorded announcements and clips"),
        ("About & Licenses", "Version 4.0 · Open Source (MIT)")
    ]
    menu_y = 330
    for title, desc in items:
        draw.rounded_rectangle([24, menu_y, SCREEN_WIDTH - 24, menu_y + 80], radius=14, fill=CARD_BG, outline=BORDER_COLOR)
        draw.text((44, menu_y + 18), title, fill=PRIMARY_TEXT, font=get_font(15, bold=True))
        draw.text((44, menu_y + 44), desc, fill=SECONDARY_TEXT, font=get_font(12))
        # Chevron >
        draw.line([SCREEN_WIDTH - 48, menu_y + 34, SCREEN_WIDTH - 42, menu_y + 40], fill=SECONDARY_TEXT, width=2)
        draw.line([SCREEN_WIDTH - 42, menu_y + 40, SCREEN_WIDTH - 48, menu_y + 46], fill=SECONDARY_TEXT, width=2)
        menu_y += 96

    img.save(out_path, "PNG", optimize=True)

def render_audio_library(out_path):
    img, draw = create_base_canvas()
    draw_top_bar(draw, "Audio Library")

    # Search bar
    search_y = 200
    draw.rounded_rectangle([24, search_y, SCREEN_WIDTH - 24, search_y + 48], radius=24, fill=CARD_BG, outline=BORDER_COLOR)
    draw.text((48, search_y + 14), "Search recordings...", fill=SECONDARY_TEXT, font=get_font(14))

    # List items
    recordings = [
        ("Announcement 2026-09-24 08-30-15.m4a", "00:42", "Today, 8:30 AM"),
        ("Live Monitoring Sample.m4a", "01:15", "Yesterday, 4:12 PM"),
        ("Hold Clip #3.m4a", "00:18", "Sep 22, 11:05 AM"),
        ("Speech Rehearsal.m4a", "03:45", "Sep 20, 2:30 PM")
    ]
    list_y = 270
    for name, dur, date in recordings:
        draw.rounded_rectangle([24, list_y, SCREEN_WIDTH - 24, list_y + 84], radius=14, fill=CARD_BG, outline=BORDER_COLOR)
        # Play circle icon
        draw.ellipse([40, list_y + 22, 80, list_y + 62], fill=PASTEL_MINT)
        draw.polygon([(56, list_y + 34), (56, list_y + 50), (68, list_y + 42)], fill=ACCENT_BLUE)

        draw.text((94, list_y + 20), name[:32] + ("..." if len(name) > 32 else ""), fill=PRIMARY_TEXT, font=get_font(14, bold=True))
        draw.text((94, list_y + 46), f"{dur}  ·  {date}", fill=SECONDARY_TEXT, font=get_font(12))
        list_y += 98

    img.save(out_path, "PNG", optimize=True)

if __name__ == "__main__":
    os.makedirs("docs/screenshots", exist_ok=True)
    render_live_microphone("docs/screenshots/live.png")
    render_hold_to_speak("docs/screenshots/hold.png")
    render_recorder("docs/screenshots/record.png")
    render_settings("docs/screenshots/settings.png")
    render_audio_library("docs/screenshots/library.png")
    print("Screenshots generated successfully under docs/screenshots/")
