"""旧文明の記録（記録片を解読すると、この順に1つずつ読めるようになる）。

RECORDS は (id, 段階, 日本語の題, 日本語の本文, 英語の題, 英語の本文)。最後の記録で、文明が滅んだ理由が分かる。
"""

RECORDS = [
    ('r01', 1, '観測記録 第1号', '本日より地表観測を開始する。我々の目標はひとつ。質量を、余すところなくエネルギーに変えること。'
     '石も、木も、海も、すべては凍りついたエネルギーにすぎない。', 'Observation Log 1',
     'Surface observation begins today. Our goal is simple: turn mass into energy, all of it. Stone, wood, the sea — '
     'all of it is frozen energy.'),
    ('r02', 1, '観測記録 第7号', '熱電変換の効率は 0.0001% に届かない。火を焚いて温度差を作るのは、山を崩して砂粒を拾うようなものだ。'
     'それでも、ここから始めるしかない。', 'Observation Log 7',
     'Thermoelectric efficiency is below 0.0001%. Burning fuel for a gradient is like moving a mountain to pick up a grain of sand. '
     'Still, this is where we must begin.'),
    ('r03', 1, '設計主任の覚え書き', '外装はすべて白にする。汚れがすぐ分かるからだ。異常は、目に見えるうちに見つけなければならない。',
     'Note from the Chief Designer', 'All casings shall be white, so that every stain shows. Anomalies must be found while they are still visible.'),
    ('r04', 1, '警備規程', '観測拠点には無人の警備機を置く。彼らは「侵入者」と「職員」を区別しない。記録を持ち出す者は、すべて侵入者である。',
     'Security Regulation', 'Unmanned guards are stationed at every post. They do not tell intruders from staff. '
     'Anyone who carries records out is an intruder.'),
    ('r05', 2, '極低温計画', '抵抗がゼロになる温度では、電流は永遠に流れ続ける。我々は永遠を手に入れた。ただし 77 K の中でだけ。',
     'Cryogenic Programme', 'At zero resistance a current flows forever. We have obtained eternity — but only at 77 kelvin.'),
    ('r06', 2, '加速器運転日誌', '衝突1,000回に1回、北しかない磁石が生まれる。理論家たちは喜んだ。技術者たちは、それを装置に挿して速度が倍になると知って、もっと喜んだ。',
     'Accelerator Logbook', 'One collision in a thousand yields a magnet with only a north pole. The theorists were delighted. '
     'The engineers, finding it doubled a machine\'s speed, were more delighted still.'),
    ('r07', 2, '触媒についての議論', '触媒は消耗品であってはならない、という意見が出た。却下された。消耗するから、人は次の段階を目指す。',
     'On Catalysts', 'Someone argued catalysts should never wear out. Rejected: it is because they wear out that we reach for the next tier.'),
    ('r08', 2, '世界線係留の倫理', 'アンカーを止めると、その区画の時間は止まる。止まった区画の中の人間は、それに気づかない。気づかないことは、問題ではないとされた。',
     'Ethics of Worldline Anchoring', 'When an anchor stops, time stops in its region. Those inside never notice. '
     'Not noticing was deemed not to be a problem.'),
    ('r09', 3, '量子部門報告', '量子もつれ素子は必ず対で生まれる。片方を壊すと、もう片方も意味を失う。我々の都市も、そうなるのだろうか。',
     'Quantum Division Report', 'Entangled elements are always born in pairs. Break one and the other loses its meaning. '
     'Will our cities be the same?'),
    ('r10', 3, '凝縮体の夢', 'ボース凝縮体の中では、すべての原子がひとつの波になる。研究員の一人は、それを「合唱」と呼んだ。彼女はその週に部署を去った。',
     'A Dream of Condensates', 'In a Bose condensate every atom becomes one wave. One researcher called it a choir. She left the division that week.'),
    ('r11', 3, '重力波検出器の初観測', '遠くで、何かが時空を揺らした。方角は分かった。距離帯も分かった。分からないのは、それが自然のものかどうかだ。',
     'First Gravitational Wave', 'Something far away shook spacetime. We know the direction and the distance band. '
     'What we do not know is whether it was natural.'),
    ('r12', 3, '研究棟閉鎖通知', '研究棟は閉鎖する。理由は公開しない。電子ロックの解除コードは、レッドストーンの信号に置き換えた。',
     'Research Building Closure', 'The research building is closed. No reason will be published. The lock codes have been replaced with redstone signals.'),
    ('r13', 4, '時間結晶', '時間結晶は、エネルギーを使わずに時を刻み続ける。我々はついに、時計そのものを育てられるようになった。',
     'Time Crystals', 'A time crystal ticks forever without spending energy. At last we can grow clocks themselves.'),
    ('r14', 4, 'C空洞の記録', '何もない空間を2枚の鏡で挟むと、そこには「何もない」よりも少ないものが生まれる。負のエネルギー。我々はそれを物質にした。',
     'C-Cavity Notes', 'Put two mirrors around nothing and you get less than nothing. Negative energy. We turned it into matter.'),
    ('r15', 4, '封鎖培養施設 最終報告', '極低温区画の温度管理に失敗。培養中の種の一部が、時間の流れから外れた。区画を封鎖する。中には、まだ職員がいる。',
     'Culture Facility Final Report', 'Cryogenic control failed. Some seeds slipped out of the flow of time. The section is sealed. Staff remain inside.'),
    ('r16', 4, '縮退圧の詩', '中性子星の物質は、ティースプーン1杯で山ひとつの重さがある。我々はそれを、ブロックの形に揃えて積み上げた。',
     'A Poem on Degeneracy', 'A teaspoon of neutron-star matter weighs as much as a mountain. We cut it into blocks and stacked it neatly.'),
    ('r17', 5, 'ペンローズ計画 承認', '回転するブラックホールのエルゴ球から、エネルギーを取り出す。理論上の効率は 42%。これで、すべての質量はエネルギーになる。計画を承認する。',
     'Penrose Project Approved', 'Energy will be drawn from the ergosphere of a spinning black hole. Theoretical efficiency: 42%. '
     'At last, all mass can become energy. The project is approved.'),
    ('r18', 5, '点火前夜', '明日、種に点火する。地平線の守護機を配置した。万一、炉心が外へ漏れたときは、施設ごと封印する手順だ。',
     'The Night Before Ignition', 'Tomorrow we ignite the seed. The horizon warden is in place. Should the core escape, the procedure is to seal the entire facility.'),
    ('r19', 5, '点火記録（断片）', '……点火成功。出力は予想の……違う、スピンが止まらない……エディントン限界を超えて……投入を止めろ……',
     'Ignition Record (fragment)', '...ignition successful. Output is... no, the spin will not stop... past the Eddington limit... stop the feed...'),
    ('r20', 5, '最後の記録', '我々はすべての質量をエネルギーに変えようとした。炉はそれを叶えた。都市も、山も、海も。'
     '残ったのは重力の異常と、守護機と、この記録だけだ。これを読む者へ。投入を止める手を、決して手放すな。',
     'The Last Record', 'We wanted to turn all mass into energy. The reactor granted that wish — the cities, the mountains, the sea. '
     'All that remains is the anomaly, the warden and this record. To whoever reads this: never let go of the hand that stops the feed.'),
]

# 封印された記録（封印コンテナの特異点錠の中の「封印記録」を読むと、この順に加わる）。形は RECORDS と同じ
HIDDEN_RECORDS = [
    ('h01', 5, '封印目録', '封印コンテナには段階ごとの鍵をかけた。次の段階に届いた者だけが、前の段階の遺産を開けられるように。'
     '急ぐ者には何も渡さない。', 'Seal Inventory',
     'Each container was locked with the key of the next stage, so that only those who reached it could open what came before. '
     'We give nothing to those in a hurry.'),
    ('h02', 5, '避難計画（未完）', '炉の暴走に備えて、一部の職員を時間の場で眠らせる計画があった。目覚める日付の欄は、最後まで空白のままだった。',
     'Evacuation Plan (Unfinished)', 'In case the reactor ran away, some staff were to sleep inside a time field. '
     'The field for the waking date was never filled in.'),
    ('h03', 5, 'これを読む誰かへ', 'これを読んでいるなら、あなたも炉に火を入れたのだろう。我々と同じ道を進むな、とは言わない。'
     'ただ、限界を見張る者をそばに置け。警報を、決して切るな。', 'To Whoever Reads This',
     'If you are reading this, you too have lit the reactor. We will not tell you to turn back. '
     'Only keep a watcher at the limit beside you, and never silence the alarm.'),
]

