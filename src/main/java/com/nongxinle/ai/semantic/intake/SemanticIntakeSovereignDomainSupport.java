package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.inheritance.SemanticContractFamilySupport;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Intake 显式 {@code primaryDomain} 主权：跨 contract family 的新业务问句不得被 WAREHOUSE bundle reconcile
 * 或 {@code SAME_CAPABILITY_TIME_OVERRIDE} 从上一轮 stable frame 抢权。
 */
public final class SemanticIntakeSovereignDomainSupport {

    private SemanticIntakeSovereignDomainSupport() {}

    public static boolean intakeDeclaresExplicitExecutableDomain(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        return SemanticIntakePrimaryDomain.isExecutable(primary);
    }

    /** 当前轮显式 primaryDomain 与上一轮 stable contract 跨族。 */
    public static boolean intakeCrossFamilyFromPreviousTurn(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        if (intake == null || previousTurn == null) {
            return false;
        }
        if (!SemanticContractFamilySupport.previousTurnHasStableBusinessFrame(previousTurn)) {
            return false;
        }
        if (!intakeDeclaresExplicitExecutableDomain(intake)) {
            return false;
        }
        String currentFamily =
                primaryDomainToContractFamily(
                        SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain()));
        String previousContractId =
                SemanticContractFamilySupport.contractIdFromPreviousTurn(previousTurn);
        String previousFamily = SemanticContractFamilySupport.resolveFamily(previousContractId);
        return SemanticContractFamilySupport.crossFamily(currentFamily, previousFamily);
    }

    /** 当前轮显式 primaryDomain 与 Intake 输入中的上一轮 stable contract 跨族。 */
    public static boolean intakeCrossFamilyFromPreviousStableContract(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null) {
            return false;
        }
        if (!SemanticContractFamilySupport.intakeHasPreviousStableContract(input)) {
            return false;
        }
        if (!intakeDeclaresExplicitExecutableDomain(intake)) {
            return false;
        }
        String currentFamily =
                primaryDomainToContractFamily(
                        SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain()));
        String previousContractId =
                SemanticContractFamilySupport.resolvePreviousStableContractIdFromIntakeInput(input);
        String previousFamily = SemanticContractFamilySupport.resolveFamily(previousContractId);
        return SemanticContractFamilySupport.crossFamily(currentFamily, previousFamily);
    }

    /** 显式跨族新能力：上一轮 frame 仅可贡献 scope/time，不得覆盖域/合同/operation。 */
    public static boolean intakeDeclaresSovereignCrossFamilyCapability(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        return intakeCrossFamilyFromPreviousStableContract(input, intake);
    }

    static String primaryDomainToContractFamily(String primaryDomain) {
        if (!StringUtils.hasText(primaryDomain)) {
            return null;
        }
        return primaryDomain.trim().toLowerCase(Locale.ROOT);
    }
}
