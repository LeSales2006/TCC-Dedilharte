package com.example.dedilharte.data;

import com.example.dedilharte.model.Lesson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo offline das trilhas do Dedilharte.
 */
public final class LessonRepository {

    public static final String BEGINNER = "Iniciante";
    public static final String INTERMEDIATE = "Intermediário";

    private static final List<Lesson> LESSONS = createLessons();

    private LessonRepository() {
    }

    public static List<Lesson> getLessons(String level) {
        List<Lesson> result = new ArrayList<>();
        for (Lesson lesson : LESSONS) {
            if (lesson.getLevel().equals(level)) {
                result.add(lesson);
            }
        }
        return result;
    }

    public static List<Lesson> getAll() {
        return LESSONS;
    }

    public static Lesson getById(String id) {
        for (Lesson lesson : LESSONS) {
            if (lesson.getId().equals(id)) {
                return lesson;
            }
        }
        return null;
    }

    private static List<Lesson> createLessons() {
        List<Lesson> lessons = new ArrayList<>();

        lessons.add(lesson(
                "ini_1", BEGINNER, 1, "Conhecendo as cordas",
                "Aprenda os nomes e a numeração das seis cordas.",
                "Reconhecer as cordas do violão e tocar cada uma com calma.",
                "As cordas são contadas de baixo para cima: a mais fina é a 1ª e a mais grossa é a 6ª. No Dedilharte, o número destacado mostra qual corda deve ser tocada.",
                new String[]{
                        "Apoie o violão de forma confortável.",
                        "Localize a 6ª corda, a mais grossa.",
                        "Toque as cordas em direção à 1ª, sem pressa.",
                        "Repita o caminho de volta e observe o som de cada corda."
                },
                new int[]{6, 5, 4, 3, 2, 1}, 52, "Cordas soltas"
        ));
        lessons.add(lesson(
                "ini_2", BEGINNER, 2, "Entendendo o app",
                "Veja como ler os exercícios e usar o modo treino.",
                "Interpretar o diagrama, os dedos PIMA e o marcador de tempo.",
                "P representa o polegar, I o indicador, M o médio e A o anelar. O marcador percorre a sequência e destaca, ao mesmo tempo, a corda e o dedo sugerido.",
                new String[]{
                        "Observe o número da corda destacada.",
                        "Confira o dedo indicado abaixo do diagrama.",
                        "Ajuste a velocidade antes de iniciar.",
                        "Use pausar e reiniciar sempre que precisar."
                },
                new int[]{6, 3, 2, 1}, 50, "Mi menor (Em)"
        ));
        lessons.add(lesson(
                "ini_3", BEGINNER, 3, "Primeiros arpejos",
                "Pratique o padrão P–I–M–A.",
                "Executar um arpejo simples mantendo a ordem dos dedos.",
                "Arpejo é quando as notas de um acorde são tocadas separadamente. Comece devagar e procure manter o mesmo intervalo entre cada toque.",
                new String[]{
                        "Faça um acorde confortável, como Dó maior.",
                        "Toque a 5ª corda com o polegar.",
                        "Use indicador, médio e anelar nas cordas 3, 2 e 1.",
                        "Repita até o movimento ficar contínuo."
                },
                new int[]{5, 3, 2, 1}, 56, "Dó maior (C)"
        ));
        lessons.add(lesson(
                "ini_4", BEGINNER, 4, "Prática de repertório",
                "Aplique o dedilhado em uma progressão inspirada em Asa Branca.",
                "Trocar acordes sem interromper o padrão da mão direita.",
                "Nesta prática, o foco não é velocidade. Mantenha o padrão do arpejo enquanto alterna entre os acordes sugeridos: C, F e G.",
                new String[]{
                        "Treine o padrão somente no acorde C.",
                        "Repita no acorde F e depois no G.",
                        "Alterne C–F–G mantendo o mesmo pulso.",
                        "Quando estiver seguro, aumente a velocidade aos poucos."
                },
                new int[]{5, 3, 2, 1, 2, 3}, 58, "C – F – G"
        ));
        lessons.add(lesson(
                "ini_5", BEGINNER, 5, "Coordenação",
                "Alterne baixos e cordas agudas.",
                "Desenvolver independência entre o polegar e os outros dedos.",
                "O polegar cuida das cordas graves, enquanto indicador, médio e anelar cuidam das agudas. A alternância ajuda a mão direita a trabalhar com mais precisão.",
                new String[]{
                        "Toque a 6ª corda com o polegar.",
                        "Toque a 2ª corda com o médio.",
                        "Mude o polegar para a 5ª corda.",
                        "Finalize na 1ª corda com o anelar."
                },
                new int[]{6, 2, 5, 1}, 54, "Mi menor (Em)"
        ));
        lessons.add(lesson(
                "ini_6", BEGINNER, 6, "Ritmo e música",
                "Mantenha o arpejo dentro de um compasso 4/4.",
                "Tocar uma sequência regular sem acelerar ou atrasar.",
                "Em 4/4, contamos quatro pulsos por compasso. O guia sonoro do treino funciona como referência para manter cada nota igualmente espaçada.",
                new String[]{
                        "Conte 1, 2, 3, 4 em voz baixa.",
                        "Toque uma corda em cada pulso.",
                        "Repita sem mudar a velocidade.",
                        "Aumente o BPM apenas quando o ritmo estiver estável."
                },
                new int[]{5, 3, 2, 1}, 64, "Lá menor (Am)"
        ));
        lessons.add(lesson(
                "ini_7", BEGINNER, 7, "Desafio iniciante",
                "Una cordas, dedos, acordes e ritmo.",
                "Executar um padrão completo com segurança e constância.",
                "O desafio final reúne as habilidades da trilha. Faça primeiro sem o guia sonoro, depois confirme sua regularidade usando o metrônomo do aplicativo.",
                new String[]{
                        "Revise a posição dos dedos PIMA.",
                        "Toque o padrão completo bem devagar.",
                        "Faça quatro repetições sem parar.",
                        "Marque a aula como concluída quando o movimento estiver confortável."
                },
                new int[]{6, 3, 2, 1, 2, 3}, 68, "Em – C – G"
        ));

        lessons.add(lesson(
                "int_1", INTERMEDIATE, 1, "Revisão do modo treino",
                "Prepare o aplicativo para exercícios mais longos.",
                "Revisar PIMA, BPM e leitura do diagrama.",
                "No nível intermediário, os padrões combinam baixos alternados e retornos pelas cordas agudas. Use velocidades menores sempre que um padrão novo aparecer.",
                new String[]{
                        "Revise a função de cada dedo.",
                        "Inicie o treino em 60 BPM.",
                        "Observe a troca entre baixo e agudos.",
                        "Aumente o ritmo somente sem tensão na mão."
                },
                new int[]{6, 3, 2, 1, 2, 3}, 60, "Mi menor (Em)"
        ));
        lessons.add(lesson(
                "int_2", INTERMEDIATE, 2, "Coordenação alternada",
                "Trabalhe o polegar em dois baixos.",
                "Manter o fluxo do arpejo enquanto o baixo muda.",
                "A alternância do polegar cria movimento no acompanhamento. Procure deixar as notas agudas com o mesmo volume, mesmo quando o baixo muda.",
                new String[]{
                        "Toque a 6ª corda com o polegar.",
                        "Complete I–M–A nas cordas agudas.",
                        "Mude o polegar para a 5ª corda.",
                        "Repita o bloco sem interromper o pulso."
                },
                new int[]{6, 3, 2, 1, 5, 3, 2, 1}, 66, "Sol maior (G)"
        ));
        lessons.add(lesson(
                "int_3", INTERMEDIATE, 3, "Coordenação II",
                "Use um padrão cruzado entre as cordas.",
                "Aprimorar precisão em movimentos que não seguem uma linha reta.",
                "Padrões cruzados exigem atenção porque a mão salta entre cordas. Priorize movimentos pequenos e evite levantar demais os dedos.",
                new String[]{
                        "Treine cada par de cordas separadamente.",
                        "Una os dois primeiros pares.",
                        "Complete o padrão em velocidade baixa.",
                        "Faça quatro ciclos sem olhar para a mão direita."
                },
                new int[]{6, 2, 4, 1, 5, 3}, 62, "Ré maior (D)"
        ));
        lessons.add(lesson(
                "int_4", INTERMEDIATE, 4, "Arpejo com retorno",
                "Suba e desça pelas cordas agudas.",
                "Executar um arpejo contínuo nos dois sentidos.",
                "O retorno evita uma pausa entre repetições. O anelar toca o ponto mais agudo e os dedos médio e indicador conduzem o caminho de volta.",
                new String[]{
                        "Suba usando P–I–M–A.",
                        "Retorne com M–I.",
                        "Mantenha o polegar preparado para reiniciar.",
                        "Busque uma transição suave entre os ciclos."
                },
                new int[]{5, 3, 2, 1, 2, 3}, 72, "Dó maior (C)"
        ));
        lessons.add(lesson(
                "int_5", INTERMEDIATE, 5, "Variações rítmicas",
                "Mude a acentuação sem mudar o padrão.",
                "Controlar dinâmica e pulsação em um arpejo.",
                "Acentuar significa destacar levemente uma nota. Experimente dar mais presença ao primeiro toque de cada grupo sem apertar ou acelerar os demais.",
                new String[]{
                        "Toque o padrão de forma uniforme.",
                        "Destaque apenas a primeira nota.",
                        "Mude o destaque para a terceira nota.",
                        "Alterne as duas formas mantendo o mesmo BPM."
                },
                new int[]{6, 3, 2, 1, 5, 3, 2, 1}, 76, "Em – D – C"
        ));
        lessons.add(lesson(
                "int_6", INTERMEDIATE, 6, "Repertório aplicado",
                "Combine uma progressão com baixo alternado.",
                "Manter clareza durante as trocas de acorde.",
                "A mão direita deve continuar regular enquanto a esquerda prepara o próximo acorde. Antecipe mentalmente a troca antes do próximo baixo.",
                new String[]{
                        "Treine o padrão em cada acorde.",
                        "Faça a troca sem o metrônomo.",
                        "Ative o guia em velocidade baixa.",
                        "Toque a progressão durante quatro ciclos."
                },
                new int[]{5, 3, 2, 1, 6, 3, 2, 1}, 74, "Am – F – C – G"
        ));
        lessons.add(lesson(
                "int_7", INTERMEDIATE, 7, "Desafio final",
                "Crie uma execução contínua e expressiva.",
                "Concluir a trilha com controle de ritmo, dinâmica e troca de baixos.",
                "O objetivo final é musicalidade, não apenas velocidade. Use o treino como guia e escolha um BPM no qual todas as notas soem claras.",
                new String[]{
                        "Comece em 60 BPM.",
                        "Faça o padrão com duas progressões.",
                        "Inclua uma acentuação por compasso.",
                        "Grave sua execução e avalie a regularidade."
                },
                new int[]{6, 3, 2, 1, 5, 2, 3, 1}, 80, "Em – C – G – D"
        ));

        return Collections.unmodifiableList(lessons);
    }

    private static Lesson lesson(
            String id,
            String level,
            int order,
            String title,
            String subtitle,
            String objective,
            String theory,
            String[] steps,
            int[] pattern,
            int bpm,
            String chord
    ) {
        return new Lesson(
                id, level, order, title, subtitle, objective, theory,
                steps, pattern, bpm, chord
        );
    }
}
