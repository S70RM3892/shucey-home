# 周期表ホーム

周期表をタップして学べる Android ホームアプリ（ランチャー）。

- 元素タップ → 和名・原子量・周期/族・分類・電子殻・価電子・電子配置・電気陰性度・入試ポイント
- クイズ：記号と番号を隠した表で位置当て。ミスした元素ほど多く出題（範囲: 1〜20 / 入試頻出 / 全118）
- ドック（最大5個、アプリ一覧で長押し→追加）、アプリ一覧（検索付き）
- 23時〜5時は表を暗くし就寝を促す
- 分類は新課程準拠（12族＝遷移元素、2族＝アルカリ土類、18族＝貴ガス）

## インストール
`dist/shucey-home.apk` を端末に入れてインストール → ホームボタン → 「周期表ホーム」を常に使う。

## ビルド
```
./gradlew :app:assembleRelease   # Android SDK 34 が必要
./gradlew :app:testReleaseUnitTest  # Robolectric でスクショ（app/build/shots）
```
元素データは `tools/gen_elements.py` で
[Periodic-Table-JSON](https://github.com/Bowserinator/Periodic-Table-JSON)（Wikipedia 由来, CC-BY-SA）から生成。
