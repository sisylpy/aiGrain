package com.nongxinle.ai.followup;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class FollowUpIntentResolveServiceTest {

    @Test
    void spliceTemporal_replaces_first_month_phrase_in_dish_profit_question() {
        String out = FollowUpIntentResolveService.spliceTemporal("上个月菜品利润怎么样", "这个月");
        Assertions.assertThat(out).isEqualTo("这个月菜品利润怎么样");
    }

    @Test
    void extractNewTemporalPhrase_finds_month_after_ne() {
        Optional<String> tp = FollowUpIntentResolveService.extractNewTemporalPhrase("那上个月呢？");
        Assertions.assertThat(tp).contains("上个月");
    }

    @Test
    void extractNewTemporalPhrase_switch_to_current_month() {
        Optional<String> tp = FollowUpIntentResolveService.extractNewTemporalPhrase("换成本月看看");
        Assertions.assertThat(tp).contains("本月");
    }

    @Test
    void extractNewTemporalPhrase_simple_month_follow_up() {
        Optional<String> tp = FollowUpIntentResolveService.extractNewTemporalPhrase("这个月呢？");
        Assertions.assertThat(tp).contains("这个月");
    }
}
