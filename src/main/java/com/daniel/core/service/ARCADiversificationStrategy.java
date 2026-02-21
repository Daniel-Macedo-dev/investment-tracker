package com.daniel.core.service;

import com.daniel.core.domain.entity.Enums.CategoryEnum;

import java.util.*;

public final class ARCADiversificationStrategy {

    // Perfil ARCA (Primo Rico)
    private static final Map<CategoryEnum, Double> ARCA_PROFILE = Map.of(
            CategoryEnum.RENDA_FIXA, 0.40,        // 40%
            CategoryEnum.RENDA_VARIAVEL, 0.30,    // 30%
            CategoryEnum.OUTROS, 0.25,            // 25% (Ativos Reais)
            CategoryEnum.CRIPTOMOEDAS, 0.05       // 5%
    );

    public record DiversificationSuggestion(
            CategoryEnum category,
            long currentCents,
            long idealCents,
            long differenceCents  // positivo = investir, negativo = reduzir
    ) {}

    /**
     * Calcula sugestões baseadas no método ARCA
     */
    public static List<DiversificationSuggestion> calculateSuggestions(
            long totalPatrimonyCents,
            Map<CategoryEnum, Long> currentAllocation
    ) {
        return calculateSuggestionsCustom(totalPatrimonyCents, currentAllocation, ARCA_PROFILE);
    }

    /**
     * Calcula sugestões com perfil customizado
     */
    public static List<DiversificationSuggestion> calculateSuggestionsCustom(
            long totalPatrimonyCents,
            Map<CategoryEnum, Long> currentAllocation,
            Map<CategoryEnum, Double> targetProfile
    ) {
        List<DiversificationSuggestion> suggestions = new ArrayList<>();

        for (CategoryEnum category : CategoryEnum.values()) {
            long currentCents = currentAllocation.getOrDefault(category, 0L);

            double targetPercentage = targetProfile.getOrDefault(category, 0.0);
            long idealCents = Math.round(totalPatrimonyCents * targetPercentage);

            long difference = idealCents - currentCents;

            suggestions.add(new DiversificationSuggestion(
                    category,
                    currentCents,
                    idealCents,
                    difference
            ));
        }

        // Ordenar por diferença absoluta (maiores primeiro)
        suggestions.sort((a, b) -> Long.compare(
                Math.abs(b.differenceCents()),
                Math.abs(a.differenceCents())
        ));

        return suggestions;
    }

    /**
     * Retorna o perfil ARCA padrão
     */
    public static Map<CategoryEnum, Double> getARCAProfile() {
        return new HashMap<>(ARCA_PROFILE);
    }

    /**
     * Valida se um perfil customizado soma 100%
     */
    public static boolean isValidProfile(Map<CategoryEnum, Double> profile) {
        double total = profile.values().stream().mapToDouble(Double::doubleValue).sum();
        return Math.abs(total - 1.0) < 0.001; // Tolerância de 0.1%
    }
}