package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从 menuFactPack（dishRows）索引 Guard 允许引用的菜品与指标。 */
public final class MenuExpertPresentationFactIndex {

    private final MenuOperationAnswerPlan plan;
    private final Set<String> allowedDishNames;
    private final Map<String, MenuExpertFactDishRow> dishByName;
    private final List<Map<String, Object>> allowedEvidenceRows;

    private MenuExpertPresentationFactIndex(
            MenuOperationAnswerPlan plan,
            Set<String> allowedDishNames,
            Map<String, MenuExpertFactDishRow> dishByName,
            List<Map<String, Object>> allowedEvidenceRows) {
        this.plan = plan;
        this.allowedDishNames = allowedDishNames;
        this.dishByName = dishByName;
        this.allowedEvidenceRows = allowedEvidenceRows;
    }

    public static MenuExpertPresentationFactIndex from(AiRunState state, MenuOperationAnswerPlan plan) {
        Map<String, MenuExpertFactDishRow> byName = MenuExpertNarrativeFactPackBuilder.indexDishRows(state, plan);
        Set<String> names = new LinkedHashSet<>(byName.keySet());
        List<Map<String, Object>> evidence =
                plan == null || plan.getEvidenceRows() == null
                        ? List.of()
                        : new ArrayList<>(plan.getEvidenceRows());
        return new MenuExpertPresentationFactIndex(plan, names, byName, evidence);
    }

    public MenuOperationAnswerPlan plan() {
        return plan;
    }

    public boolean isKnownDish(String dishName) {
        return StringUtils.hasText(dishName) && allowedDishNames.contains(dishName.trim());
    }

    public Set<String> allowedDishNames() {
        return allowedDishNames;
    }

    public MenuExpertFactDishRow dishFacts(String dishName) {
        if (!StringUtils.hasText(dishName)) {
            return null;
        }
        return dishByName.get(dishName.trim());
    }

    public List<Map<String, Object>> allowedEvidenceRows() {
        return allowedEvidenceRows;
    }

    public boolean anyDishHasNegativeProfit() {
        for (MenuExpertFactDishRow dish : dishByName.values()) {
            if (dish != null
                    && MenuExpertPresentationPlanGuardSupport.isNegativeAmount(dish.actualProfitAmount())) {
                return true;
            }
        }
        return false;
    }

    /** Guard 专用：仍可读 AnswerPlan 中 watch/观察分组，不进入 LLM 输入。 */
    public boolean planAllowsDelistLanguage() {
        if (plan == null || plan.getMenuOptimizationPlan() == null) {
            return false;
        }
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        if (containsDelistHintFromDishes(optimization.getWatchListDishes())) {
            return true;
        }
        List<MenuOptimizationPriorityGroup> groups = optimization.getPriorityGroups();
        if (groups == null) {
            return false;
        }
        for (MenuOptimizationPriorityGroup group : groups) {
            if (group == null) {
                continue;
            }
            if (MenuExpertPresentationPlanGuardSupport.containsDelistHint(group.getSuggestedAction())
                    || MenuExpertPresentationPlanGuardSupport.containsDelistHint(group.getReason())
                    || containsDelistHintFromDishes(group.getDishes())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDelistHintFromDishes(List<MenuOptimizationDishItem> dishes) {
        if (dishes == null) {
            return false;
        }
        for (MenuOptimizationDishItem dish : dishes) {
            if (dish == null) {
                continue;
            }
            if (MenuExpertPresentationPlanGuardSupport.containsDelistHint(dish.getSuggestedActionLabel())
                    || MenuExpertPresentationPlanGuardSupport.containsDelistHint(dish.getReason())) {
                return true;
            }
        }
        return false;
    }
}
