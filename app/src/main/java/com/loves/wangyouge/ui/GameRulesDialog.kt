package com.loves.wangyouge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class RuleSection(val title: String, val body: String)

@Composable
fun GameRulesDialog(game: RuleGame, onDismiss: () -> Unit) {
    val sections = remember(game) { if (game == RuleGame.DOUDIZHU) doudizhuRules else loveLetterRules }
    var selected by remember(game) { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (game == RuleGame.DOUDIZHU) "斗地主规则" else "情书规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp) {
                    sections.forEachIndexed { index, section ->
                        Tab(
                            selected = selected == index,
                            onClick = { selected = index },
                            text = { Text(section.title, fontSize = 12.sp) }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(sections[selected].body, lineHeight = 21.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

enum class RuleGame { DOUDIZHU, LOVE_LETTER }

private val doudizhuRules = listOf(
    RuleSection("目标", "斗地主由一名地主对抗两名农民。谁先出完手牌，谁所在的一方获胜。每局结束后按身份和倍数结算场内分数。"),
    RuleSection("流程", "开局先叫地主，三名玩家依次选择叫或不叫；无人叫地主时重新发牌。确定地主后，地主取得三张底牌并先出牌，之后按逆时针轮流行动。轮到你时可以出更大的同类牌，也可以选择不出。"),
    RuleSection("阵营", "地主独自作战，需要尽快减少手牌并控制出牌权。农民两人同队：地主报单或接近走完时应积极拦截；队友出牌时通常不要压队友，尽量给队友创造走牌机会。"),
    RuleSection("牌型", "单牌、对子、三张、三带一、三带二、顺子、连对、飞机、四带二都可以出。顺子至少五张且不能包含 2 和王；连对至少两对连续对子；飞机由连续三张组成，可带单牌或对子；四带二需要带两张不同单牌或两对。"),
    RuleSection("比较", "单牌、对子、三张、顺子、连对、飞机、四带二只能和张数与牌型相同的牌比较点数。炸弹可以压过普通牌型，王炸最大；更大的炸弹可以压更小的炸弹。"),
    RuleSection("结算", "地主先出完牌则地主得分、两名农民扣分；任一农民先出完牌则农民得分、地主扣分。炸弹和王炸会提高倍数，具体场内倍数以牌桌显示为准。")
)

private val loveLetterRules = listOf(
    RuleSection("目标", "每位玩家起手一张牌。轮到你时摸一张，再从两张手牌中打出一张并执行效果。淘汰其他玩家，或牌堆耗尽时保留最高点数，即可赢得本轮并获得一枚信物。"),
    RuleSection("流程", "从自己的回合开始摸牌，然后选择一张打出。行动结束后轮到下一位仍存活的玩家。侍女的保护持续到该玩家下一次回合开始；牌堆耗尽时比较仍存活玩家的手牌点数，点数相同再比较已打出牌的总点数。"),
    RuleSection("目标选择", "守卫、祭司、男爵、国王需要选择一名其他且仍存活的目标。受到侍女保护的玩家不能成为效果目标；目标已淘汰或选择自己都会被拒绝。"),
    RuleSection("牌面效果", "守卫：猜目标手牌点数，猜中淘汰目标（不能猜守卫）。祭司：查看目标手牌。男爵：双方比较手牌点数，较低者淘汰。侍女：本回合免疫其他玩家效果。王子：让目标弃牌并补牌，弃掉公主会被淘汰。"),
    RuleSection("高点牌", "国王：与目标交换手牌。伯爵夫人：没有主动效果，但手里同时有国王或王子时必须打出。公主：一旦被打出或因王子效果弃掉，立即淘汰。"),
    RuleSection("结算", "一轮只剩一名存活玩家时立即结束；如果牌堆耗尽，则按存活玩家手牌点数判定。赢家获得信物并记录一场正式情书战绩，纯 AI 或单真人陪练不计入个人历史战绩。")
)
