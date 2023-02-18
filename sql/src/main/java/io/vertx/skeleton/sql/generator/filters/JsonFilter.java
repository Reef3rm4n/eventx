package io.vertx.skeleton.sql.generator.filters;

import io.smallrye.mutiny.tuples.Tuple3;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class JsonFilter {

    private JsonFilter(){}

    public static void addFieldJson(StringJoiner queryFilters, Map<String, Object> paramMap, Tuple3<String, String, List<?>> tuple) {
        if (tuple.getItem2() != null && !tuple.getItem2().isEmpty()) {
            if (tuple.getItem3().stream().anyMatch(Integer.class::isInstance)) {
                JsonFilter.anyJsonInt(tuple.getItem1(), tuple.getItem2(), tuple.getItem3().stream().map(Integer.class::cast).toList(), paramMap, queryFilters);
            }
            if (tuple.getItem3().stream().anyMatch(String.class::isInstance)) {
                JsonFilter.anyJsonString(tuple.getItem1(), tuple.getItem2(), tuple.getItem3().stream().map(String.class::cast).toList(), paramMap, queryFilters);
            }
            if (tuple.getItem3().stream().anyMatch(Enum.class::isInstance)) {
                JsonFilter.anyJsonEnum(tuple.getItem1(), tuple.getItem2(), tuple.getItem3().stream().map(Enum.class::cast).toList(), paramMap, queryFilters);
            }
            if (tuple.getItem3().stream().anyMatch(Long.class::isInstance)) {
                JsonFilter.anyJsonLong(tuple.getItem1(), tuple.getItem2(), tuple.getItem3().stream().map(Long.class::cast).toList(), paramMap, queryFilters);
            }
        }
    }

    public static void anyJsonInt(String column, String field, List<Integer> params, Map<String, Object> paramMap, StringJoiner queryString) {
        if (params != null && !params.isEmpty()) {
            final var array = params.toArray(Integer[]::new);
            paramMap.put(column, array);
            queryString.add(" " + column + " ->> '" + field + "' ilike any(#{" + column + "}) ");
        }
    }

    public static void anyJsonLong(String column, String field, List<Long> params, Map<String, Object> paramMap, StringJoiner queryString) {
        if (params != null && !params.isEmpty()) {
            final var array = params.toArray(Long[]::new);
            paramMap.put(column, array);
            queryString.add(" " + column + " ->> '" + field + "' ilike any(#{" + column + "}) ");
        }
    }

    public static void anyJsonString(String column, String field, List<String> params, Map<String, Object> paramMap, StringJoiner queryString) {
        if (params != null && !params.isEmpty()) {
            final var array = params.stream().map(s -> s.replace("*", "%")).toArray(String[]::new);
            paramMap.put(column, array);
            queryString.add(" " + column + " ->> '" + field + "' ilike any(#{" + column + "}) ");
        }
    }

    public static void anyJsonEnum(String column, String field, List<Enum> params, Map<String, Object> paramMap, StringJoiner queryString) {
        if (params != null && !params.isEmpty()) {
            final var array = params.stream().map(Enum::name).toArray(String[]::new);
            paramMap.put(column, array);
            queryString.add(" " + column + " ->> '" + field + "' ilike any(#{" + column + "}) ");
        }
    }

}
