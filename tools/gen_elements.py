#!/usr/bin/env python3
"""Generate app/src/main/assets/elements.json.

Source data: Bowserinator/Periodic-Table-JSON (CC-BY-SA, Wikipedia-derived)
  https://raw.githubusercontent.com/Bowserinator/Periodic-Table-JSON/master/PeriodicTableJSON.json
Japanese names / classification / exam notes are maintained here by hand
(classification follows the 2022 Japanese high-school curriculum:
 12族は遷移元素、2族は全てアルカリ土類金属、18族は「貴ガス」).

Usage: python3 tools/gen_elements.py PeriodicTableJSON.json app/src/main/assets/elements.json
"""
import json, sys

JA = ("水素 ヘリウム リチウム ベリリウム ホウ素 炭素 窒素 酸素 フッ素 ネオン "
      "ナトリウム マグネシウム アルミニウム ケイ素 リン 硫黄 塩素 アルゴン カリウム カルシウム "
      "スカンジウム チタン バナジウム クロム マンガン 鉄 コバルト ニッケル 銅 亜鉛 "
      "ガリウム ゲルマニウム ヒ素 セレン 臭素 クリプトン ルビジウム ストロンチウム イットリウム ジルコニウム "
      "ニオブ モリブデン テクネチウム ルテニウム ロジウム パラジウム 銀 カドミウム インジウム スズ "
      "アンチモン テルル ヨウ素 キセノン セシウム バリウム ランタン セリウム プラセオジム ネオジム "
      "プロメチウム サマリウム ユウロピウム ガドリニウム テルビウム ジスプロシウム ホルミウム エルビウム ツリウム イッテルビウム "
      "ルテチウム ハフニウム タンタル タングステン レニウム オスミウム イリジウム 白金 金 水銀 "
      "タリウム 鉛 ビスマス ポロニウム アスタチン ラドン フランシウム ラジウム アクチニウム トリウム "
      "プロトアクチニウム ウラン ネプツニウム プルトニウム アメリシウム キュリウム バークリウム カリホルニウム アインスタイニウム フェルミウム "
      "メンデレビウム ノーベリウム ローレンシウム ラザホージウム ドブニウム シーボーギウム ボーリウム ハッシウム マイトネリウム ダームスタチウム "
      "レントゲニウム コペルニシウム ニホニウム フレロビウム モスコビウム リバモリウム テネシン オガネソン").split()
assert len(JA) == 118

# 非金属元素（典型）。B, Si, As, Te は教科書では非金属側に置かれることが多い。
NONMETAL = {1, 2, 5, 6, 7, 8, 9, 10, 14, 15, 16, 17, 18, 33, 34, 35, 36, 52, 53, 54, 85, 86, 117, 118}

# 入試で問われる性質のメモ（教科書範囲の確立した事実のみ）
NOTE = {
    1: "最も軽い気体。Zn＋希硫酸で発生（水上置換）。燃えると水を生じる。",
    2: "貴ガス。2番目に軽い気体。最外殻電子は2個だが価電子は0とみなす。",
    3: "炎色反応：赤。最も軽い金属。水と反応してH₂を発生。",
    5: "共有結合結晶をつくる。ホウケイ酸ガラスの原料。",
    6: "同素体：ダイヤモンド・黒鉛・フラーレン・カーボンナノチューブ。黒鉛は電気を通す。",
    7: "空気の約78%（体積）。NH₃はハーバー・ボッシュ法（Fe触媒）、HNO₃はオストワルト法（Pt触媒）。",
    8: "同素体：O₂とO₃（オゾン）。空気の約21%（体積）。",
    9: "電気陰性度が最大。F₂は水と激しく反応しO₂を発生。HFは弱酸でガラス（SiO₂）を溶かす。",
    10: "貴ガス。ネオンサイン（赤橙色）。",
    11: "炎色反応：黄。石油中に保存。NaOHは潮解性。Na₂CO₃はアンモニアソーダ法（ソルベー法）。",
    12: "炎色反応を示さない。熱水と反応しH₂を発生。2族（アルカリ土類金属）。",
    13: "両性金属（酸にも強塩基にも溶ける）。溶融塩電解で製造。濃硝酸には不動態。",
    14: "共有結合結晶。単体は半導体。SiO₂はHFにのみ溶ける（フッ化水素酸）。",
    15: "同素体：黄リン（自然発火→水中保存）と赤リン。P₄O₁₀は強い乾燥剤。",
    16: "同素体：斜方硫黄・単斜硫黄・ゴム状硫黄。H₂SO₄は接触法（V₂O₅触媒）。",
    17: "黄緑色の有毒気体。MnO₂＋濃塩酸（加熱）で発生。水に溶けHClOを生じ漂白・殺菌作用。",
    18: "空気中で3番目に多い成分（約0.93%）。貴ガス。",
    19: "炎色反応：赤紫。石油中に保存。",
    20: "炎色反応：橙赤。CaCO₃（石灰石）、CaO（生石灰）、Ca(OH)₂（消石灰）。",
    24: "CrO₄²⁻（黄）⇄ Cr₂O₇²⁻（橙赤）。酸性で二クロム酸イオン。Cr₂O₇²⁻は強い酸化剤→Cr³⁺（緑）。",
    25: "MnO₄⁻（赤紫）は酸性で強い酸化剤→Mn²⁺（ほぼ無色）。MnO₂は H₂O₂分解の触媒。",
    26: "Fe²⁺（淡緑）＋K₃[Fe(CN)₆]→濃青色沈殿。Fe³⁺（黄褐）＋K₄[Fe(CN)₆]→濃青色沈殿、＋KSCN→血赤色溶液。濃硝酸には不動態。",
    27: "Co²⁺水溶液は淡赤色。塩化コバルト紙：乾燥時青→吸湿で赤。",
    28: "Ni²⁺水溶液は緑色。濃硝酸には不動態。",
    29: "炎色反応：青緑。Cu²⁺水溶液は青。過剰NH₃水で[Cu(NH₃)₄]²⁺（深青）。電解精錬で純銅。",
    30: "両性金属。過剰NH₃水で[Zn(NH₃)₄]²⁺（無色）。ZnSは白色沈殿（唯一の白色硫化物）。",
    35: "常温で液体の非金属（赤褐色）。",
    38: "炎色反応：紅（深赤）。",
    47: "電気・熱伝導性が最大の金属。AgCl白・AgBr淡黄・AgI黄、感光性。Ag₂O褐色、[Ag(NH₃)₂]⁺無色。",
    50: "両性金属。SnCl₂は還元剤。ブリキ（Fe表面にSn）。",
    53: "黒紫色の固体で昇華性。デンプン水溶液で青紫（ヨウ素デンプン反応）。",
    55: "炎色反応：青紫。イオン化エネルギーが非常に小さい。",
    56: "炎色反応：黄緑。BaSO₄は白色沈殿（X線造影剤）。BaCO₃も白色沈殿。",
    78: "王水にのみ溶ける。触媒（オストワルト法など）。",
    79: "展性・延性が最大。王水にのみ溶ける。",
    80: "常温で液体の唯一の金属。合金はアマルガム。",
    82: "PbCl₂白（熱水に溶ける）、PbSO₄白、PbCrO₄黄、PbS黒。鉛蓄電池の負極。",
    113: "日本（理化学研究所）が発見し命名した初の元素。",
}

def cat_of(n, x, y):
    if y >= 9:
        return "lanthanoid" if y == 9 else "actinoid"
    if x == 1 and n != 1:
        return "alkali"
    if x == 2:
        return "alkaline_earth"
    if 3 <= x <= 12:
        return "transition"
    if x == 17:
        return "halogen"
    if x == 18:
        return "noble"
    return "nonmetal" if n in NONMETAL else "metal"

def fmt_mass(n, m):
    # 安定同位体のない元素は最も長寿命な同位体の質量数を [ ] で示す
    if n in (43, 61) or (n >= 84 and n not in (90, 91, 92)):
        return "[%d]" % round(m)
    s = ("%.3f" % m).rstrip("0").rstrip(".")
    return s

def main(src, dst):
    els = json.load(open(src, encoding="utf-8"))["elements"]
    out = []
    for e in els:
        n = e["number"]
        if n > 118:
            continue
        x, y = e["xpos"], e["ypos"]
        cfg = e["electron_configuration_semantic"]
        predicted = cfg.startswith("*")
        cfg = cfg.lstrip("*")
        phase = {"Gas": "気体", "Liquid": "液体", "Solid": "固体"}[e["phase"]]
        if n >= 100:
            phase = "不明"
        out.append({
            "n": n, "s": e["symbol"], "ja": JA[n - 1], "en": e["name"],
            "m": fmt_mass(n, e["atomic_mass"]),
            "x": x, "y": y, "p": e["period"],
            "g": x if y <= 7 else 3,
            "c": cat_of(n, x, y),
            "cfg": cfg + ("（予測）" if predicted else ""),
            "sh": e["shells"],
            "en_p": e["electronegativity_pauling"],
            "ph": phase,
            "note": NOTE.get(n, ""),
        })
    assert len(out) == 118
    json.dump(out, open(dst, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
