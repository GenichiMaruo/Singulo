"""旧文明の記録（クリスタルメモリを解読すると、この順に1つずつ読めるようになる）。

RECORDS は (id, 段階, 日本語の題, 日本語の本文, 英語の題, 英語の本文)。最後の記録で、文明が滅んだ理由が分かる。
"""

RECORDS = [
    ('r01', 1, '観測記録 第1号', '本日、地表の観測を始める。目標はただひとつ、質量をあますところなくエネルギーに変えることだ。'
     '石も、木も、海も、凍りついたエネルギーにすぎない。', 'Observation Log 1',
     'Surface observation begins today. Our goal is simple: turn mass into energy, all of it. Stone, wood, the sea — '
     'all of it is frozen energy.'),
    ('r02', 1, '観測記録 第7号', '熱電変換の効率は 0.0001% にも届かない。火を焚いて温度差を作るなど、砂粒ひとつ拾うために山を崩すようなものだ。'
     'それでも、ここから始めるしかない。', 'Observation Log 7',
     'Thermoelectric efficiency is below 0.0001%. Burning fuel for a gradient is like moving a mountain to pick up a grain of sand. '
     'Still, this is where we must begin.'),
    ('r03', 1, '設計主任の覚え書き', '外装はすべて白で統一する。汚れがひと目で分かるからだ。異常は、目に見えるうちに見つけなければならない。',
     'Note from the Chief Designer', 'All casings shall be white, so that every stain shows. Anomalies must be found while they are still visible.'),
    ('r04', 1, '警備規程', '各観測拠点には無人の警備機を配備する。警備機は侵入者と職員を区別しない。'
     '記録を持ち出そうとする者は、誰であれ侵入者とみなす。',
     'Security Regulation', 'Unmanned guards are stationed at every post. They do not tell intruders from staff. '
     'Anyone who carries records out is an intruder.'),
    ('r05', 2, '極低温計画', '抵抗がゼロになれば、電流は永遠に流れ続ける。我々はついに永遠を手に入れた。もっとも、77 ケルビンの中に限っての話だが。',
     'Cryogenic Programme', 'At zero resistance a current flows forever. We have obtained eternity — but only at 77 kelvin.'),
    ('r06', 2, '加速器運転日誌', '衝突千回に一度、北極しか持たない磁石が生まれる。理論家たちは歓声を上げた。'
     'それを装置に組み込めば速さが倍になると知った技術者たちは、もっと喜んだ。',
     'Accelerator Logbook', 'One collision in a thousand yields a magnet with only a north pole. The theorists were delighted. '
     'The engineers, finding it doubled a machine\'s speed, were more delighted still.'),
    ('r07', 2, '触媒をめぐる議論', '触媒は使っても減らないものにすべきだ、という意見が出た。却下された。すり減るからこそ、人は次の段階を目指すのだ。',
     'On Catalysts', 'Someone argued catalysts should never wear out. Rejected: it is because they wear out that we reach for the next tier.'),
    ('r08', 2, '世界線係留の倫理', 'アンカーが止まると、その区画の時間も止まる。中にいる者は、そのことに気づかない。'
     'そして、気づかないのなら問題はない、という結論になった。',
     'Ethics of Worldline Anchoring', 'When an anchor stops, time stops in its region. Those inside never notice. '
     'Not noticing was deemed not to be a problem.'),
    ('r09', 3, '量子部門報告', '量子もつれ素子は、必ず対で生まれる。片方が壊れれば、もう片方も意味を失う。我々の都市も、いつかそうなるのだろうか。',
     'Quantum Division Report', 'Entangled elements are always born in pairs. Break one and the other loses its meaning. '
     'Will our cities be the same?'),
    ('r10', 3, '凝縮体の夢', 'ボース凝縮体の中では、すべての原子がひとつの波になる。ある研究員は、それを「合唱」と呼んだ。'
     '彼女は、その週のうちに部署を去った。',
     'A Dream of Condensates', 'In a Bose condensate every atom becomes one wave. One researcher called it a choir. She left the division that week.'),
    ('r11', 3, '重力波の初観測', '遠いどこかで、何かが時空を揺らした。方角は分かった。おおよその距離も分かった。'
     '分からないのは、それが自然に起きたものかどうかだ。',
     'First Gravitational Wave', 'Something far away shook spacetime. We know the direction and the distance band. '
     'What we do not know is whether it was natural.'),
    ('r12', 3, '研究棟閉鎖通知', '研究棟を閉鎖する。理由は公表しない。電子錠の解除コードは、レッドストーン信号に置き換えた。',
     'Research Building Closure', 'The research building is closed. No reason will be published. The lock codes have been replaced with redstone signals.'),
    ('r13', 4, '時間結晶', '時間結晶は、エネルギーを使わずに時を刻み続ける。我々はついに、時計そのものを育てられるようになった。',
     'Time Crystals', 'A time crystal ticks forever without spending energy. At last we can grow clocks themselves.'),
    ('r14', 4, 'C空洞の記録', '何もない空間を2枚の鏡で挟むと、そこには「何もない」よりもさらに少ないものが生まれる。負のエネルギーだ。'
     '我々はそれを、物質に変えた。',
     'C-Cavity Notes', 'Put two mirrors around nothing and you get less than nothing. Negative energy. We turned it into matter.'),
    ('r15', 4, '封鎖培養施設 最終報告', '極低温区画の温度管理に失敗した。培養中の種の一部が、時間の流れからこぼれ落ちた。区画を封鎖する。'
     '中には、まだ職員が残っている。',
     'Culture Facility Final Report', 'Cryogenic control failed. Some seeds slipped out of the flow of time. The section is sealed. Staff remain inside.'),
    ('r16', 4, '縮退圧の詩', '中性子星の物質は、ティースプーン1杯で山ひとつ分の重さになる。我々はそれをブロックの形に切りそろえ、整然と積み上げた。',
     'A Poem on Degeneracy', 'A teaspoon of neutron-star matter weighs as much as a mountain. We cut it into blocks and stacked it neatly.'),
    ('r17', 5, 'ペンローズ計画 承認', '回転するブラックホールのエルゴ球から、エネルギーを取り出す。理論上の効率は 42%。'
     'これで、すべての質量をエネルギーに変えられる。本計画を承認する。',
     'Penrose Project Approved', 'Energy will be drawn from the ergosphere of a spinning black hole. Theoretical efficiency: 42%. '
     'At last, all mass can become energy. The project is approved.'),
    ('r18', 5, '点火前夜', '明日、種に点火する。地平線の守護機は、すでに配置を終えた。'
     '万一、炉心が外へ漏れ出したときは、施設ごと封印する手はずになっている。',
     'The Night Before Ignition', 'Tomorrow we ignite the seed. The horizon warden is in place. Should the core escape, the procedure is to seal the entire facility.'),
    ('r19', 5, '点火記録（断片）', '……点火に成功。出力は予想の……違う、スピンが止まらない……エディントン限界を超えて……投入を止めろ……',
     'Ignition Record (fragment)', '...ignition successful. Output is... no, the spin will not stop... past the Eddington limit... stop the feed...'),
    ('r20', 5, '最後の記録', '我々は、すべての質量をエネルギーに変えようとした。炉はその願いを叶えた。都市も、山も、海も。'
     '残ったのは、重力の異常と、守護機と、この記録だけだ。これを読む者へ。炉に何かを注ぎ込むなら、止めるべき時に必ず止めろ。我々には、それができなかった。',
     'The Last Record', 'We wanted to turn all mass into energy. The reactor granted that wish — the cities, the mountains, the sea. '
     'All that remains is the anomaly, the warden and this record. To whoever reads this: if you feed the reactor, stop when it is time to stop. We could not.'),
]

# 最後の記録者を倒すと加わる記録。形は RECORDS と同じ
FINAL_RECORDS = [
    ('x01', 5, '最後の頁', '私は、眠る者たちの番をしていた。時間の場が止まったとき、記録と、場を支えていた特異点が、私の中へ流れ込んできた。'
     'それから何千年が過ぎたのかは分からない。読む者が現れるまで、私は記録そのものとなって待ち続けた。'
     'あなたは、最後の頁まで読んでくれた。私も、もう眠っていいだろう。どうか、炉の火を見守っていてほしい。', 'The Last Page',
     'I kept watch over the sleepers. When the time field stopped, the records and the singularity that held the field flowed into me. '
     'I do not know how many millennia have passed. Until a reader came, I waited as the records themselves. '
     "You have read to the last page. I think I may sleep now. Please keep watch over the reactor's fire."),
]
# 創世炉で宇宙を生むと加わる記録。x03 は最後の記録者を倒した人だけ
GENESIS_RECORDS = [
    ('x02', 5, '最初の頁', 'すべての質量は、エネルギーになった。その炉の火で、私たちはもう一度、宇宙をひとつ生み出した。星がともり、銀河が渦を巻く。'
     'いつかそこにも、記録を残す誰かが生まれるだろう。その誰かもまた、止めるべき時に止められますように。', 'The First Page',
     'All mass became energy. With that same fire, we made a universe once more. '
     'Stars kindle and a galaxy turns. Someday someone there will be born to keep records of their own. '
     'May they, too, know when to stop.'),
    ('x03', 5, '記録者への返信', '最後の頁の先に、新しい最初の頁を書いた。眠る番人へ。あなたの記録は読まれ、そして宇宙になった。もう見張らなくていい。'
     'これからは、私たちが火を見守る。', 'A Reply to the Archivist',
     'Beyond the last page, I wrote a new first page. To the watcher who sleeps: your records were read, '
     'and they became a universe. You need not keep watch any longer. We are watching the fire now.'),
]

# 封印された記録（封印コンテナの特異点錠の中の「封印記録」を読むと、この順に加わる）。形は RECORDS と同じ
HIDDEN_RECORDS = [
    ('h01', 5, '封印目録', '封印コンテナには、それぞれ次の段階の鍵をかけた。次の段階にたどり着いた者だけが、前の段階の遺産を開けられるように。'
     '先を急ぐ者には、何も渡さない。', 'Seal Inventory',
     'Each container was locked with the key of the next stage, so that only those who reached it could open what came before. '
     'We give nothing to those in a hurry.'),
    ('h02', 5, '避難計画（未完）', '炉の暴走に備えて、職員の一部を時間の場で眠らせておく計画があった。目覚める日付の欄は、最後まで空白のままだった。',
     'Evacuation Plan (Unfinished)', 'In case the reactor ran away, some staff were to sleep inside a time field. '
     'The field for the waking date was never filled in.'),
    ('h03', 5, 'これを読む誰かへ', 'これを読んでいるのなら、あなたも炉に火を入れたのだろう。我々と同じ道を行くな、とは言わない。'
     'ただ、限界を見張る者を必ずそばに置け。そして、警報だけは決して切るな。', 'To Whoever Reads This',
     'If you are reading this, you too have lit the reactor. We will not tell you to turn back. '
     'Only keep a watcher at the limit beside you, and never silence the alarm.'),
]

