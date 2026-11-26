import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
public class Main {
    //код разделен на 5  блоков:
    // 1.осн настройки со списком слов кот нельзя трогать
    // 2.работа с файлами
    // 3.анализ исходного кода
    // 4.преобразование
    // 5.обработка(удаление пробелов и пустых строк)

    // БЛОК 1. ОСНОВНЫЕ НАСТРОЙКИ

    //ключевые слова java, которые нельзя переименовывать
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var"
    ));

    //имена стандартной библиотеки java, которые тоже не трогаем
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "System", "out", "in", "err", "println", "print", "printf", "main", "args", "String", "Integer", "Double", "Float", "Long", "Short", "Byte", "Boolean",
            "Character", "Object", "Math", "Scanner", "Exception", "RuntimeException", "Throwable", "Error", "List", "ArrayList", "Map", "HashMap", "Set", "HashSet",
            "Arrays", "Collections", "Override", "toString", "equals", "hashCode", "length", "size", "get", "put", "add", "remove", "valueOf", "parseInt", "parseDouble", "nextInt",
            "nextLine", "next", "StringBuilder", "StringBuffer", "Thread", "Runnable", "Comparable", "Iterator", "Random", "Files", "Paths", "IOException", "FileNotFoundException"
    ));

    // БЛОК 2. РАБОТА С ФАЙЛАМИ

    public static void main(String[] args) throws IOException {

        //проверяем указан ли путь к папке
        if (args.length == 0) {
            System.out.println("укажите путь к папке с файлами");
            return;
        }
        File folder = new File(args[0]);
        //получаем список .java файлов
        File[] files = getJavaFiles(folder);//тут переход к методу поиска
        if (files == null||files.length == 0) {
            System.out.println("файлы .java не найдены");
            return;
        }
        //обработка найденных файлов
        for (File file : files) {
            changeFile(file);
        }
    }

    //метод поиска файлов с расширением .java
    private static File[] getJavaFiles(File folder) {
        if (!folder.exists()|| !folder.isDirectory()) {//если не нашли папку
            return null;
        }
        return folder.listFiles((dir, name) -> name.endsWith(".java"));//тут поиск
    }

    //основная обработка файла
    private static void changeFile(File file) throws IOException {
        //читаем исходный код
        String text = readFile(file);
        //последовательно выполняем преобразования
        text = deleteComments(text);
        String className = getClassName(text);
        if (className == null) {
            System.out.println("класс не найден: " + file.getName());
            return;
        }
        List<String> identifiers = getIdentifiers(text, className);
        Map<String, String> renameMap = createRenameMap(identifiers);
        //меняем имя класса
        renameMap.put(className, "A");

        text = renameWords(text, renameMap);
        text = cleanText(text);
        //сохраняем результат
        saveFile(file, "A", text);
    }

    //чтение содержимого файла
    private static String readFile(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        return new String(data, StandardCharsets.UTF_8);
    }

    //сохранение обработанного кода
    private static void saveFile(File original, String name, String text)
            throws IOException {
        //для каждого исходного файла создаем отдельную папку
        File resultFolder = new File(
                original.getParentFile(),
                "obfuscated/" + original.getName()
        );
        if (!resultFolder.exists()) {
            resultFolder.mkdirs();
        }
        File result = new File(resultFolder, name + ".java");
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(result), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
        System.out.println("готово: " + original.getName() + " -> " + result.getPath());
    }

    // 3. АНАЛИЗ ИСХОДНОГО КОДА

    //удаление однострочных и многострочных комментариев
    private static String deleteComments(String text) {
        StringBuilder result = new StringBuilder();
        boolean stringMode = false;
        boolean charMode = false;
        boolean lineMode = false;
        boolean blockMode = false;
        for (int i = 0; i < text.length(); i++) {
            char symbol = text.charAt(i);
            char next = i + 1 < text.length()
                    ? text.charAt(i + 1) : '\0';
            if (lineMode) {
                if (symbol == '\n') {
                    lineMode = false;
                    result.append(symbol);
                }
                continue;
            }
            if (blockMode) {
                if (symbol == '*' && next == '/') {
                    blockMode = false;
                    i++;
                }
                else if (symbol == '\n') {
                    result.append(symbol);
                }
                continue;
            }
            if (stringMode || charMode) {
                result.append(symbol);
                if (symbol == '\\' && next != '\0') {
                    result.append(next);
                    i++;
                    continue;
                }
                if (stringMode && symbol == '"') {
                    stringMode = false;
                } else if (charMode && symbol == '\'') {
                    charMode = false;
                }
                continue;
            }
            if (symbol == '/' && next == '/') {
                lineMode = true;
                i++;
            }
            else if (symbol == '/' && next == '*') {
                blockMode = true;
                i++;
            }
            else {
                if (symbol == '"') {
                    stringMode = true;
                }
                else if (symbol == '\'') {
                    charMode = true;
                }
                result.append(symbol);
            }
        }
        return result.toString();
    }

    //получение имени класса
    private static String getClassName(String text) {
        Pattern pattern = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    //проверка можно ли переименовывать найденное слово
    private static boolean isAllowedName(String name, String className) {
        if (KEYWORDS.contains(name)) {
            return false;
        }
        if (SKIP.contains(name)) {
            return false;
        }
        return !name.equals(className);
    }
    //поиск идентификаторов в исходном коде
    private static List<String> getIdentifiers(String text, String className) {
        Set<String> names = new LinkedHashSet<>();
        boolean stringMode = false;
        boolean charMode = false;
        for (int i = 0; i < text.length(); i++) {
            char symbol = text.charAt(i);
            if (stringMode || charMode) {
                if (symbol == '\\' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                if (stringMode && symbol == '"') {
                    stringMode = false;
                }
                else if (charMode && symbol == '\'') {
                    charMode = false;
                }
                continue;
            }
            if (symbol == '"') {
                stringMode = true;
                continue;
            }
            if (symbol == '\'') {
                charMode = true;
                continue;
            }
            //проверяем начало идентификатора
            if (Character.isJavaIdentifierStart(symbol)) {
                StringBuilder word = new StringBuilder();
                word.append(symbol);
                //собираем остальные символы слова
                while (i + 1 < text.length()
                        && Character.isJavaIdentifierPart(text.charAt(i + 1))) {

                    word.append(text.charAt(++i));
                }
                String name = word.toString();
                if (isAllowedName(name, className)) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    // 4. ПРЕОБРАЗОВАНИЕ КОДА

    //создание таблицы замены имен
    private static Map<String, String> createRenameMap(List<String> words) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> shortNames = getShortNames(words.size());
        for (int i = 0; i < words.size(); i++) {
            result.put(words.get(i), shortNames.get(i));
        }
        return result;
    }
    //генерация коротких имен
    private static List<String> getShortNames(int count) {
        List<String> result = new ArrayList<>();
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        //сначала используем однобуквенные имена
        for (int i = 0; i < alphabet.length(); i++) {
            String name = String.valueOf(alphabet.charAt(i));
            if (!KEYWORDS.contains(name)) {
                result.add(name);
            }
            if (result.size() >= count) {
                return result;
            }
        }
        //если букв недостаточно создаем имена из 2 букв
        for (int i = 0; i < alphabet.length(); i++) {
            for (int j = 0; j < alphabet.length(); j++) {
                String name = "" + alphabet.charAt(i) + alphabet.charAt(j);
                if (!KEYWORDS.contains(name)) {
                    result.add(name);
                }
                if (result.size() >= count) {
                    return result;
                }
            }
        }
        return result;
    }

    //замена старых идентификаторов на новые
    private static String renameWords(String text, Map<String, String> replacements) {
        StringBuilder result = new StringBuilder();
        boolean stringMode = false;
        boolean charMode = false;
        for (int i = 0; i < text.length(); i++) {
            char symbol = text.charAt(i);
            //строки и символы оставляем без изменений
            if (stringMode || charMode) {
                result.append(symbol);
                if (symbol == '\\' && i + 1 < text.length()) {
                    result.append(text.charAt(++i));
                    continue;
                }
                if (stringMode && symbol == '"') {
                    stringMode = false;
                }
                else if (charMode && symbol == '\'') {
                    charMode = false;
                }
                continue;
            }
            if (symbol == '"') {
                stringMode = true;
                result.append(symbol);
                continue;
            }
            if (symbol == '\'') {
                charMode = true;
                result.append(symbol);
                continue;
            }

            //если найден идентификатор проверяем его в таблице
            if (Character.isJavaIdentifierStart(symbol)) {
                StringBuilder word = new StringBuilder();
                word.append(symbol);
                while (i + 1 < text.length() && Character.isJavaIdentifierPart(text.charAt(i + 1))) {
                    word.append(text.charAt(++i));
                }
                String oldName = word.toString();
                if (replacements.containsKey(oldName)) {
                    result.append(replacements.get(oldName));
                }
                else {
                    result.append(oldName);
                }
            } else {
                result.append(symbol);
            }
        }
        return result.toString();
    }

    // 5. ЗАВЕРШАЮЩАЯ ОБРАБОТКА

    //удаление лишних пробелов и пустых строк
    private static String cleanText(String text) {
        //убираем пустые строки
        text = text.replaceAll("(?m)^[ \\t]*\\r?\\n", "");
        //убираем пробелы в начале и конце строк
        text = text.replaceAll("(?m)^[ \\t]+", "");
        text = text.replaceAll("(?m)[ \\t]+$", "");
        //заменяем несколько пробелов одним
        text = text.replaceAll("[ \\t]{2,}", " ");
        return text;
    }
}
