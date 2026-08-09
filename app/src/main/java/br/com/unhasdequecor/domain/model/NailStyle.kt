package br.com.unhasdequecor.domain.model

enum class NailStyle(val displayName: String) {
    CLASSICO("Clássico"),
    DELICADO("Delicado"),
    ELEGANTE("Elegante"),
    DIVERTIDO("Divertido"),
    OUSADO("Ousado"),
    MINIMALISTA("Minimalista"),
    ROMANTICO("Romântico"),
    FASHIONISTA("Fashionista"),
    MODERNO("Moderno"),
    VERSATIL("Versátil"),
    NEUTRO("Neutro"),
    SOFISTICADO("Sofisticado"),
    MARCANTE("Marcante"),
    SUAVE("Suave"),
    FRESCO("Fresco"),
    ATEMPORAL("Atemporal"),
    CLEAN("Clean"),
}

enum class Occasion(val displayName: String) {
    DIA_A_DIA("Dia a dia"),
    ENCONTRO("Encontro"),
    TRABALHO("Trabalho"),
    FESTA("Festa"),
    VIAGEM("Viagem"),
    EM_CASA("Em casa"),
}

enum class Mood(val displayName: String) {
    ROMANTICA("Romântica"),
    TRANQUILA("Tranquila"),
    CRIATIVA("Criativa"),
    ENERGETICA("Energética"),
    NEUTRA("Neutra"),
}

enum class Season(val displayName: String) {
    PRIMAVERA("Primavera"),
    VERAO("Verão"),
    OUTONO("Outono"),
    INVERNO("Inverno"),
}

enum class RecommendationSource {
    CONTEXT,
    FOR_ME,
}
