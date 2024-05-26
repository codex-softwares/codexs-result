package ch.codexs.util;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Result<T, I> {

    private final T content;
    private final List<I> issues;

    /**
     * Builds a result with a content.
     *
     * @param content non-null reference to the content to hold the returned {@code Result}.
     * @return Result wrapping the provided content.
     * @param <U> The type of the content.
     * @param <I> The type of the issues (empty).
     */
    public static <U, I> Result<U, I> succeeded(U content) {
        return new Result<>(Objects.requireNonNull(content), List.of());
    }


    /**
     * Builds a result with a content and issues.
     *
     * @param content non-null reference to the content to hold the returned {@code Result}.
     * @param firstIssue Issue to include.
     * @param issues Array of additional issues to include.
     * @return Result wrapping the provided content and issues.
     * @param <U> The type of the content.
     * @param <I> The type of the issues.
     */
    @SafeVarargs
    public static <U, I> Result<U, I> succeeded(U content, I firstIssue, I... issues) {
        return new Result<>(
                Objects.requireNonNull(content),
                Stream.concat(
                        Stream.of(Objects.requireNonNull(firstIssue)),
                        Arrays.stream(issues)
                ).collect(Collectors.toList())
        );
    }

    /**
     * Builds a result without a content (failed).
     *
     * @param issues The issues.
     * @return A new {@code Result}
     * @param <U> The type of the content.
     * @param <I> The type of the issues.
     */
    public static <U, I> Result<U, I> failed(List<I> issues) {
        if (issues.isEmpty()) {
            throw new IllegalArgumentException("At least one issue is expected");
        }
        return new Result<>(null, issues);
    }

    /**
     * Builds a result without a content (failed).
     *
     * @param firstIssue The first issue.
     * @param issues The following issues.
     * @return A new {@code Result}
     * @param <U> The type of the content.
     * @param <I> The type of the issues.
     */
    @SafeVarargs
    public static <U, I> Result<U, I> failed(I firstIssue, I... issues) {
        return new Result<>(
                null,
                Stream.concat(
                        Stream.of(
                                Objects.requireNonNull(firstIssue)
                        ),
                        Arrays.stream(issues)
                ).collect(Collectors.toList())
        );
    }

    public interface ThrowingSupplier<T, E extends Throwable> {
        T supply() throws E;
    }

    public static class Catcher<E extends Throwable> {
        private final Class<E> throwableClass;

        Catcher(Class<E> throwableClass) {
            this.throwableClass = throwableClass;
        }

        /**
         * Runs the provided {@code ThrowingSupplier}, gets its result and build a {@code Result} out of it.
         * If there is a throw exception, and it is expected, it is catch and used as an issue to build the result.
         *
         * @param supplier ThrowingSupplier providing a content. Should not provide {@code null}.
         * @return Result either containing the supplied content or the catch exception as an issue.
         * @param <T> The type of the content.
         */
        public <T> Result<T, E> run(ThrowingSupplier<T, E> supplier) {
            try {
                return Result.succeeded(supplier.supply());
            } catch (Throwable exception) {
                if (throwableClass.isAssignableFrom(throwableClass)) {
                    return Result.failed(throwableClass.cast(exception));
                } else {
                    throw new RuntimeException(exception);
                }
            }
        }
    }

    /**
     *
     * @param throwableClass Class of the expected throwable to catch.
     * @return Catcher to run the following supplier in a exception safe manner.
     * @param <E> The type of throwable to catch.
     */
    public static <E extends Throwable> Catcher<E> catching(Class<E> throwableClass) {
        return new Catcher<>(throwableClass);
    }

    /**
     * Construct a {@code Result} in a simple way. If the content is null, then the result is considered as failed.
     *
     * @param content An object being the content of the result.
     * @param issues List of issues, non null.
     */
    public Result(T content, List<I> issues) {
        this.content = content;
        this.issues = Objects.requireNonNull(issues);
    }

    /**
     * Indicates if the result has failed, in other words, is there a content in it.
     *
     * @return true if the content is missing.
     */
    public boolean hasFailed() {
        return content == null;
    }

    /**
     * Get the issues.
     *
     * @return the issues.
     */
    public List<I> issues() {
        return issues;
    }

    /**
     * Map the content (if any).
     *
     * @param mapper {@code Function}
     * @return A new {@code Result} with mapped content.
     * @param <U> The type of the mapped content.
     */
    public <U> Result<U, I> map(Function<T, U> mapper) {
        return content == null ?
                new Result<>(null, issues) :
                new Result<>(mapper.apply(content), issues);
    }

    /**
     * Map the content (if any) to another {@code Result}.
     *
     * @param mapper {@code Function} to transform the content (if any).
     * @return A new {@code Result} with mapped content.
     * @param <U> The type of the mapped content.
     */
    public <U> Result<U, I> flatMap(Function<T, Result<U, I>> mapper) {
        if (content == null) {
            return failed(issues);
        } else {
            var mappingResult = mapper.apply(content);
            return new Result<>(mappingResult.content, concatIssues(issues, mappingResult.issues));
        }
    }

    /**
     * Map the issues to transform them.
     *
     * @param mapper {@code Function} to transform the issues (if any).
     * @return A new {@code Result} with mapped issues.
     * @param <J> The type of the mapped issues.
     */
    public <J> Result<T, J> mapIssues(Function<I, J> mapper) {
        return new Result<>(content, issues.stream().map(mapper).collect(Collectors.toList()));
    }

    /**
     * Map both the content and the issues to build a new {@code Result} with them.
     *
     * @param contentMapper {@code Function} to transform the content. Will only be called if the content is present.
     * @param issueMapper {@code Function} to transform the issues. Will only be called if the issues are present.
     * @return {@code Result} - a new result with the mapped content and issues.
     * @param <U> The type of the mapped content.
     * @param <J> The type of the mapped issues.
     */
    public <U, J> Result<U, J> mapBoth(Function<T, U> contentMapper, Function<I, J> issueMapper) {
        var mappedIssues = issues.stream().map(issueMapper).collect(Collectors.toList());
        if (content != null) {
            return new Result<>(contentMapper.apply(content), mappedIssues);
        } else {
            return new Result<>(null, mappedIssues);
        }
    }

    /**
     * Creates a new {@code Result}, copy of this one, but with added issues at the end.
     *
     * @param issueLists the issues to add.
     * @return The new {@code Result} including new issues.
     */
    @SafeVarargs
    public final Result<T, I> withAddedIssues(Iterable<I>... issueLists) {
        return new Result<>(
                content,
                concatIssues(this.issues, issueLists)
        );
    }

    private Result<T, I> withPrependedIssues(List<I> issueLists) {
        return new Result<>(
                content,
                concatIssues(issueLists, this.issues)
        );
    }

    /**
     * Creates a new {@code Result}, copy of this one, but with added issues at the end.
     *
     * @param issues the issues to add.
     * @return The new {@code Result} including new issues.
     */
    @SafeVarargs
    public final Result<T, I> withAddedIssues(I... issues) {
        return new Result<>(
                content,
                Stream.concat(
                        this.issues.stream(),
                        Arrays.stream(issues)
                ).collect(Collectors.toList())
        );
    }

    @SafeVarargs
    private static <I> List<I> concatIssues(List<I> issues, Iterable<I>... issueLists) {
        return Stream.concat(
                issues.stream(),
                Stream.of(issueLists).flatMap(iterable -> StreamSupport.stream(iterable.spliterator(), false))
        ).collect(Collectors.toList());
    }

    /**
     * Give conditional access to the issues. The consumer is called only if there are contained issues.
     * Can be followed by the call to a getter returning the content, null if the result has no content.
     *
     * @param issueConsumer the consumer to process issues
     * @return {@code ThenValueProcessor} - to get the content
     */
    public ThenValueProcessor ifIssuesExistDo(Consumer<List<I>> issueConsumer) {
        if (!issues.isEmpty()) {
            issueConsumer.accept(issues);
        }
        return new ThenValueProcessor();
    }

    /**
     * Give conditional access to the issues. The consumer is called only if there is no content in the result.
     * Can be followed by the call to a getter returning the content, null if the result has no content.
     *
     * @param issueConsumer {@code Consumer} to process issues
     * @return {@code ElseValueProcessor} - to get the content
     */
    public ElseValueProcessor ifFailedDo(Consumer<List<I>> issueConsumer) {
        if (hasFailed()) {
            issueConsumer.accept(issues);
        }
        return new ElseValueProcessor();
    }

    /**
     * Transforms the issues into an output in case of missing content, using the given function.
     * The output is not accessible straight away but is transmitted to the returned {@code ValueTransformer}.
     *
     * @param issueTransformer A function to transform the issues into a value that will be returned if there was no success.
     * @param <R>              The type of the output resulting from the transformation provided
     * @return {@code ValueTransformer} - allowing to unwrap the content and transform it into a another value in case of success
     */
    public <R> ValueTransformer<R> ifFailedTransform(Function<List<I>, R> issueTransformer) {
        final R valueOutOfIssues;
        if (hasFailed()) {
            valueOutOfIssues = issueTransformer.apply(issues);
        } else {
            valueOutOfIssues = null;
        }
        return new ValueTransformer<>(valueOutOfIssues);
    }

    public class ValueTransformer<R> {
        R upstreamValue;

        ValueTransformer(R upstreamValue) {
            this.upstreamValue = upstreamValue;
        }

        /**
         * Give access to both the content and the issues in order to build an output.
         *
         * @param valuetransformer The function to map both the content and the issues.
         * @return The mapping result.
         */
        public R orElseUnwrap(BiFunction<T, List<I>, R> valuetransformer) {
            return Optional.ofNullable(this.upstreamValue).orElseGet(() -> valuetransformer.apply(content, issues));
        }
    }

    /**
     * Give access to both the content and the issues in order to build an output.
     * If the content is absent ({@code hasFailed()} returns true) the chained issue transformation is applied.
     *
     * @param mapper The function to map both the content and the issues.
     * @return {@code IssueTransformer<U, I>} enabling the transformation of the issues.
     * @param <U> The type of the mapped content.
     */
    public <U> IssueTransformer<U, I> unwrap(BiFunction<T, List<I>, U> mapper) {
        if (!hasFailed()) {
            return new IssueTransformer<>(mapper.apply(content, issues), issues);
        } else {
            return new IssueTransformer<>(null, issues);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result<?, ?> result = (Result<?, ?>) o;
        return Objects.equals(content, result.content) && Objects.equals(issues, result.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, issues);
    }

    public final class BiCombiner<U> {

        private final Result<U, I> other;

        BiCombiner(Result<U, I> other) {
            this.other = other;
        }

        /**
         * Merges the contents of the two results.
         *
         * @param combiner BiFunction to combine the contents if both exist.
         * @return a new {@code Result<V, I>} containing the merged content.
         * @param <V>
         */
        public <V> Result<V, I> mergeMap(BiFunction<T, U, V> combiner) {
            var concatenatedIssues = Stream.concat(Result.this.issues.stream(), other.issues.stream()).collect(Collectors.toList());

            if (Result.this.content == null || other.hasFailed()) {
                return Result.failed(concatenatedIssues);
            } else {
                return new Result<>(combiner.apply(Result.this.content, other.content), concatenatedIssues);
            }
        }

        public <V> Result<V, I> mergeFlatMap(BiFunction<T, U, Result<V, I>> combiner) {
            var concatenatedIssues = Stream.concat(Result.this.issues.stream(), other.issues.stream()).collect(Collectors.toList());

            if (Result.this.content == null || other.hasFailed()) {
                return Result.failed(concatenatedIssues);
            } else {
                return combiner.apply(Result.this.content, other.content).withPrependedIssues(concatenatedIssues);
            }
        }
    }

    /**
     * Combines this result with another one
     *
     * @param other Result to combine with.
     * @return BiCombiner to decide how to actually combine values.
     * @param <U> the type of the mapped content.
     */
    public <U> BiCombiner<U> combineWith(Result<U, I> other) {
        return new BiCombiner<>(other);
    }

    /**
     * Combines the provided results into one single {@code Result<List<T>, I>}.
     * As provided results may not be all successful, they are filtered and the resulting list may be smaller than the provided result list.
     * On the other hand, all the issues are collected, including the one of the failed provided results.
     *
     * @param results Collection of results to combine.
     * @return Result containing the contents of the provided collection of results.
     * @param <T> The type of the content.
     * @param <I> The type of the issues.
     */
    public static <T, I> Result<List<T>, I> combineAllSuccessful(Collection<Result<T, I>> results) {
        var issues = results.stream()
                .flatMap(r -> r.issues.stream())
                .collect(Collectors.toList());

        var values = results.stream()
                .filter(result -> !result.hasFailed())
                .flatMap(result -> Stream.ofNullable(result.content))
                .collect(Collectors.toList());

        return new Result<>(values, issues);
    }

    public abstract class ValueProcessor {

        /**
         * Give conditional access to the content.
         *
         * @param valueConsumer Consumer to process the content.
         */
        public void ifSucceededDo(Consumer<T> valueConsumer) {
            if (content != null) {
                valueConsumer.accept(content);
            }
        }

        /**
         * Give conditional access to the content and the issues.
         *
         * @param valueConsumer BiConsumer to process both the content and the issues.
         */
        public void ifSucceededDo(BiConsumer<T, List<I>> valueConsumer) {
            if (content != null) {
                valueConsumer.accept(content, issues);
            }
        }
    }

    public class ThenValueProcessor extends ValueProcessor {

        /**
         * Get the content, null or non-null.
         *
         * @return the content, null or non-null.
         */
        public T thenGetContent() {
            return content;
        }
    }

    public class ElseValueProcessor extends ValueProcessor {

        /**
         * Get the content, null or non-null.
         *
         * @return the content, null or non-null.
         */
        public T elseGetContent() {
            return content;
        }
    }

    public static class IssueTransformer<T, I> {

        private final T upstreamValue;
        private final List<I> issues;

        IssueTransformer(T upstreamValue, List<I> issues) {
            this.upstreamValue = upstreamValue;
            this.issues = issues;
        }

        /**
         * Transforms the issues into a returned value.
         *
         * @param issueTransformation Function to map the issues, if no upstream value exist.
         * @return an object, the result of the mapping of the issues.
         */
        public T orElse(Function<List<I>, T> issueTransformation) {
            if (upstreamValue != null) {
                return upstreamValue;
            } else {
                return issueTransformation.apply(issues);
            }
        }
    }
}
