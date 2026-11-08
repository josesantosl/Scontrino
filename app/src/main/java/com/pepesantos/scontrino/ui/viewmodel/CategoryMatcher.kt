package com.pepesantos.scontrino.ui.viewmodel

import androidx.annotation.StringRes
import com.pepesantos.scontrino.R
import java.text.Normalizer

/**
 * Utility object for automatic product categorization.
 * Matches keywords found in product names to category labels.
 *
 * Classification rule: a product goes where it is used, not by what it is made of
 * (soy milk is dairy, pesto and ragù are condiments, a lasagna you just heat is a ready meal).
 */
object CategoryMatcher {

    /**
     * Keyword rules, checked in order: the first category with a matching keyword wins, so
     * more specific categories come first (cat food with chicken is pets, "tonno all'olio" is
     * seafood, "salsa di soia" is a condiment, "latte di soia" is dairy, "pasta de dientes" is
     * personal care, "succo di mela" is a drink).
     *
     * Keywords are matched lowercase and without accents (Italian, Spanish, English, French
     * and German). A keyword matches as a substring, so stems like "spaghett" cover plurals;
     * keywords prefixed with `=` (and any keyword of up to 2 letters) only match whole words,
     * for short words that hide inside others ("sal" in "salmone", "pan" in "panna").
     */
    private val rules: List<Pair<String, List<String>>> = listOf(
        "pets" to listOf(
            "mascota", "=pet", "=pets", "perro", "=dog", "=dogs", "per cani", "gatt", "gato", "=cat", "=cats",
            "=chat", "=chien", "hund", "katze", "felix", "whiskas", "purina", "almo nature", "friskies",
            "sheba", "pedigree", "lettiera",
        ),
        "baby" to listOf(
            "panal", "pannolin", "diaper", "=couches", "windel", "potito", "omogeneizzat", "plasmon",
            "mellin", "=hipp", "nidina", "biberon", "pampers", "huggies", "dodot", "neonat",
        ),
        "health" to listOf(
            "farma", "pharma", "medic", "=pill", "pastilla", "aspirin", "tachipirin", "ibuprofen",
            "paracetamol", "cerott", "tirita", "apothek",
        ),
        "household" to listOf(
            "limpieza", "cleaning", "detergent", "detersiv", "lavavajill", "lavastovigl", "lavapiatti",
            "sapone piatti", "lavatrice", "candeggin", "lejia", "bleach", "javel", "ammorbident",
            "suavizante", "spugn", "esponja", "sponge", "carta igienica", "papel higienico",
            "toilet paper", "carta cucina", "papel de cocina", "rotolon", "scottex", "tovagliol",
            "servillet", "napkin", "alluminio", "aluminio", "pellicola", "sacchett", "=bolsa", "=bolsas",
            "=pila", "=pilas", "batteri", "bateria", "lampadin", "bombilla", "insetticid", "sgrassat",
            "anticalcare", "ammoniaca", "dixan", "svelto",
        ),
        "personal_hygiene" to listOf(
            "shampoo", "champu", "=gel", "bagnoschiuma", "doccia", "dentifric", "pasta de dientes",
            "toothpaste", "dental", "diente", "spazzolin", "cepillo", "toothbrush", "deodor", "desodor",
            "hygiene", "higiene", "igiene", "sapone", "jabon", "=soap", "savon", "seife", "assorbent",
            "compresa", "tampon", "rasoi", "maquinilla", "razor", "balsamo", "acondicionador",
            "colluttorio", "enjuague",
        ),
        "transport" to listOf(
            "=bus", "=train", "treno", "metro", "biglietto", "billete", "ticket", "transport",
            "benzina", "gasolina", "diesel", "carburante", "=fuel", "parcheggio", "parking",
        ),
        "eating_out" to listOf(
            "ristorante", "restaurant", "trattoria", "pizzeria", "coperto", "=bar", "=menu",
        ),
        "ready_meals" to listOf(
            "piatto pronto", "piatti pronti", "plato preparado", "platos preparados", "ready meal",
            "plat prepare", "fertiggericht", "lasagn", "pizza", "sushi", "zuppa", "minestron", "=sopa",
            "=soup", "empanad", "nugget", "croquet", "kebab", "burrito", "tramezzin",
        ),
        "seafood" to listOf(
            "pescado", "pesce", "=fish", "poisson", "fisch", "salmon", "=atun", "tonno", "=tuna", "=thon",
            "merluz", "nasello", "baccala", "bacalao", "gamber", "=gamba", "=gambas", "shrimp", "prawn",
            "crevette", "calamar", "pulpo", "polpo", "mejillon", "cozze", "mussel", "vongol", "almeja",
            "sardin", "acciug", "anchoa", "anchov", "=orata", "dorada", "branzino", "lubina", "spigola",
            "surimi", "marisco", "frutti di mare", "seafood", "fruits de mer", "trucha", "trota",
            "sgombro", "caballa", "mackerel", "=cod",
        ),
        "condiments" to listOf(
            "aceite", "olio", "=oil", "huile", "vinagre", "vinegar", "aceto", "essig", "=sal", "=salt",
            "sale fino", "sale grosso", "sale marino", "=sel", "=salz", "pimienta", "=pepe", "=pepper",
            "=poivre", "especia", "spezie", "spice", "epice", "gewurz", "salsa", "sauce", "sugo", "pesto",
            "ragu", "passata", "pelati", "tomate frito", "tomate triturado", "polpa di pomodoro",
            "ketchup", "mayonesa", "maionese", "mayonnaise", "=mayo", "mostaza", "senape", "mustard",
            "moutarde", "=senf", "=dado", "brodo", "margarin",
        ),
        // After condiments: "aceto di vino" is vinegar.
        "alcohol" to listOf(
            "birra", "=beer", "cerveza", "biere", "=bier", "=vino", "=vini", "=wine", "prosecco",
            "spumante", "lambrusco", "chianti", "rioja", "cava brut", "champagne", "whisky", "vodka",
            "=gin", "=rum", "=rhum", "tequila", "liquor", "licor", "aperol", "spritz", "sidra", "cidre",
            "=cider", "grappa", "limoncello",
        ),
        "dairy" to listOf(
            "leche", "latte", "=milk", "=lait", "milch", "yogur", "joghurt", "yaourt", "queso", "formagg",
            "fromage", "kase", "cheese", "mozzarell", "=mozz", "parmigian", "=grana", "pecorino",
            "ricotta", "mascarpone", "stracchino", "gorgonzola", "scamorza", "provola", "emmental",
            "=brie", "=feta", "=skyr", "kefir", "burro", "mantequilla", "butter", "beurre", "=nata",
            "=panna", "lacteo", "latticin", "dairy", "bebida de soja", "bebida de avena",
            "bebida de almendra", "bevanda di soia", "bevanda di avena", "bevanda di mandorla",
        ),
        "legumes" to listOf(
            "tofu", "seitan", "tempeh", "soja", "soya", "=soia", "legumbre", "legumi", "legumin",
            "lenteja", "lenticch", "lentil", "lentille", "linse", "garbanzo", "=ceci", "chickpea",
            "pois chiche", "kichererbse", "fagiol", "frijol", "alubia", "=beans", "haricot", "bohne",
            "hummus", "edamame",
        ),
        "meat" to listOf(
            "pollo", "chicken", "poulet", "=huhn", "hahnchen", "carne", "=meat", "viande", "fleisch",
            "=pork", "cerdo", "maiale", "=porc", "schwein", "=beef", "manzo", "ternera", "vitello",
            "bovino", "boeuf", "=rind", "=vaca", "=ham", "jamon", "prosciutt", "=prosc", "salame",
            "salami", "salsiccia", "salchicha", "sausage", "saucisse", "wurst", "bacon", "pancetta",
            "speck", "mortadella", "bresaola", "chorizo", "=lomo", "tacchino", "=pavo", "turkey",
            "dinde", "=pute", "cordero", "agnello", "=lamb", "hamburg", "macinato", "picada", "polpett",
            "albondig", "=coppa", "embutido",
        ),
        "bakery" to listOf(
            "=pan", "=pane", "=pain", "=brot", "bread", "panin", "baguette", "focaccia", "piadin",
            "tostad", "fette biscottate", "croissant", "cornett", "brioche", "bolleria", "=bollo",
            "bauletto", "grissin", "magdalena", "muffin",
        ),
        "pasta_rice" to listOf(
            "pasta", "spaghett", "maccheron", "=penne", "fusill", "rigaton", "tagliatell", "farfalle",
            "linguine", "gnocchi", "tortellin", "ravioli", "noodle", "fideo", "nudel", "=pates",
            "arroz", "riso", "=rice", "=riz", "=reis", "cereal", "avena", "=oats", "fiocchi", "muesli",
            "granola", "harina", "farina", "flour", "farine", "=mehl", "cuscus", "couscous", "quinoa",
            "polenta", "semola",
        ),
        "eggs" to listOf(
            "huevo", "=uova", "=uovo", "=egg", "=eggs", "oeuf", "œuf", "=eier",
        ),
        "snacks_sweets" to listOf(
            "galleta", "biscott", "cookie", "biscuit", "=keks", "chocolate", "ciocco", "cioccolat",
            "schokolad", "caramel", "candy", "bonbon", "chicle", "snack", "patatine", "papas fritas",
            "patatas fritas", "=chips", "crisps", "cracker", "tarall", "pringles", "nutella", "merendin",
            "gelato", "helado", "ice cream", "mermelada", "marmellat", "confettur", "=jam", "confiture",
            "=miel", "miele", "honey", "honig", "azucar", "zucchero", "sugar", "=sucre", "zucker",
            "=torta", "=tarta", "=cake", "dolce", "dulce", "turron", "panettone", "pandoro", "wafer",
            "kinder", "haribo", "barrett",
        ),
        "beverages" to listOf(
            "agua", "acqua", "=water", "=eau", "wasser", "jugo", "juice", "zumo", "succo", "=jus", "=saft",
            "=soda", "refresco", "bibita", "=cola", "coca cola", "aranciata", "gassosa", "limonata",
            "tonica", "tonic", "=te", "=tea", "infusion", "infuso", "tisana", "coffee", "cafe", "caffe",
            "kaffee", "espresso", "capsul", "nescafe",
        ),
        "vegetables_fruit" to listOf(
            "frutta", "fruta", "fruit", "=obst", "verdura", "verdure", "vegetable", "ortaggi", "gemuse",
            "manzana", "=mela", "=mele", "apple", "pomme", "apfel", "banan", "platano", "=pera", "=pere",
            "=pear", "poire", "birne", "=uva", "grape", "naranj", "aranc", "orange", "limon", "lemon",
            "citron", "fresa", "fragol", "strawberr", "fraise", "lechug", "lattuga", "insalat",
            "lettuce", "salade", "tomate", "pomodor", "tomato", "patata", "potato", "kartoffel",
            "cebolla", "cipoll", "onion", "oignon", "zwiebel", "zanahoria", "carota", "carote", "carrot",
            "carotte", "karotte", "=ajo", "aglio", "garlic", "zucchin", "calabac", "courgett",
            "melanzan", "berenjena", "aubergine", "eggplant", "peperon", "pimiento", "spinac", "espinac",
            "spinat", "broccol", "brocol", "cavol", "=col", "cabbage", "=kohl", "funghi", "champi",
            "mushroom", "=pilz", "avocad", "aguacate", "=kiwi", "melon", "anguria", "sandia",
            "watermelon", "=pesca", "pesche", "durazno", "melocot", "peach", "albicocc", "apricot",
            "ciliegi", "cerez", "cherr", "ananas", "=pina", "mango", "papaya", "frutos rojos",
            "frutti di bosco", "mirtill", "arandano", "blueberr", "rucola", "rucula", "carciof",
            "alcachof", "finocch", "sedano", "=apio", "cetriol", "pepino", "cucumber", "porro",
            "puerro", "=leek",
        ),
    )

    private class Keyword(val text: String, val wholeWord: Regex?)

    private val compiledRules: List<Pair<String, List<Keyword>>> = rules.map { (label, keywords) ->
        label to keywords.map { raw ->
            val text = normalize(raw.removePrefix("="))
            val wholeWord = raw.startsWith("=") || text.length <= 2
            Keyword(text, if (wholeWord) Regex("""\b${Regex.escape(text)}\b""") else null)
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

    /**
     * Attempts to find a matching category label for a given product name.
     * @param name The name entered by the user.
     * @return The label of the matched category, or null if no keywords match.
     */
    fun detectCategoryLabel(name: String): String? {
        val normalizedName = normalize(name)
        return compiledRules.firstOrNull { (_, keywords) ->
            keywords.any { keyword ->
                keyword.wholeWord?.containsMatchIn(normalizedName) ?: normalizedName.contains(keyword.text)
            }
        }?.first
    }

    /**
     * Returns the string resource ID for a category label, or null if custom / not found.
     */
    @StringRes
    fun getCategoryStringRes(label: String): Int? {
        return when (label) {
            "other" -> R.string.category_other
            "vegetables_fruit" -> R.string.category_vegetables_fruit
            "meat" -> R.string.category_meat
            "seafood" -> R.string.category_seafood
            "dairy" -> R.string.category_dairy
            "eggs" -> R.string.category_eggs
            "legumes" -> R.string.category_legumes
            "bakery" -> R.string.category_bakery
            "pasta_rice" -> R.string.category_pasta_rice
            "condiments" -> R.string.category_condiments
            "snacks_sweets" -> R.string.category_snacks_sweets
            "ready_meals" -> R.string.category_ready_meals
            "beverages" -> R.string.category_beverages
            "alcohol" -> R.string.category_alcohol
            "household" -> R.string.category_household
            "personal_hygiene" -> R.string.category_personal_hygiene
            "baby" -> R.string.category_baby
            "pets" -> R.string.category_pets
            "health" -> R.string.category_health
            "transport" -> R.string.category_transport
            "eating_out" -> R.string.category_eating_out
            else -> null
        }
    }
}
