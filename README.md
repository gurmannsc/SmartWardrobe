# Smart Wardrobe

An Android app (Java) that catalogues your clothes with AI and suggests outfits for an occasion.

- **Snap and tag:** photograph a clothing item and the AI fills in category, type, colours, pattern, formality and seasons. You correct anything wrong before saving.
- **Indian and Western wear:** kurta, sherwani, saree, lehenga, juttis, dupatta and more, alongside jeans, blazers and sneakers.
- **Dress me:** pick an occasion (college, office, interview, festive, wedding and so on) and the AI stylist builds up to 3 outfits from *your* clothes. It considers colour theory, today's weather, what's in the laundry and what you haven't worn lately.
- **Wear log:** tap an outfit to log it. Suggestions then favour clothes you rarely wear.

Made by Gurmann Singh Chiraunde (H037), MAD course, NMIMS.

## Setup

1. Open this folder in Android Studio.
2. Copy `secrets.properties.example` to `secrets.properties` and paste your Groq key ([console.groq.com/keys](https://console.groq.com/keys)):
   ```
   GROQ_API_KEY=gsk_...
   ```
3. Run the app. **Settings** should say "Groq key found".

The key goes into the app as `BuildConfig.GROQ_API_KEY` when you build. `secrets.properties` is git-ignored, so the key never reaches GitHub.

## Building an APK to share

**Quick (debug APK):** *Build > Generate App Bundles or APKs > Generate APKs*. The file appears at `app/build/outputs/apk/debug/app-debug.apk`. It installs on any Android 7.0+ phone once "Install unknown apps" is allowed.

**Proper (signed release APK):** *Build > Generate Signed App Bundle or APK > APK*, then create a keystore when asked (keep it safe, since you need it for every future update) and choose **release**. Output: `app/release/app-release.apk`.

Anyone who unpacks the APK could extract the Groq key. The worst case with a free key is that its daily limit runs out, so set a spending limit if you ever add billing.

## How it's built

| Screen | Files | What it shows |
|---|---|---|
| Home | `MainActivity` / `activity_main.xml` | Weather card, stats, main buttons |
| Add item | `AddItemActivity` / `activity_add_item.xml` | Camera or gallery (implicit intents), AI identify |
| Item editor | `ItemEditorActivity` / `activity_item_editor.xml` | Spinners, RadioGroup, CheckBoxes. Confirms new items and edits saved ones |
| Wardrobe | `WardrobeActivity` / `activity_wardrobe.xml` + `list_item_wardrobe.xml` | ListView + SimpleAdapter with a category filter |
| Occasion | `OccasionActivity` / `activity_occasion.xml` | RadioGroup of occasions, optional note |
| Outfits | `OutfitsActivity` / `activity_outfits.xml` + `list_item_outfit.xml` | AI outfits; tap one to log the wear |
| Settings | `SettingsActivity` / `activity_settings.xml` | City for weather, AI key check |

Helper classes:
- `WardrobeDbHelper`: the SQLite `items` table.
- `GroqClient`: AI requests using `HttpURLConnection` and `org.json`, with no extra libraries.
- `WeatherClient`: Open-Meteo, which is free and needs no key.
- `ImageUtils`: resizes and rotates photos.
- `ClothingItem`: the data model.

All user-facing text, clothing lists, AI prompts and URLs are in `res/values/strings.xml`. All colours are in `res/values/colors.xml`.

**AI:** Groq's `qwen/qwen3.8-27b` vision model, free tier with 1,000 requests a day. To switch providers (for example OpenRouter), change `groq_url` and `groq_model` in `strings.xml`.
