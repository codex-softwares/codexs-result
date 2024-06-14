package ch.codexs.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ResultTest {

    @Test
    void should_fail_if_content_is_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Result.succeeded(null));
    }

    @Test
    void should_fail_if_issues_are_null() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Result.failed(List.of()));
    }

    @Test
    void should_fail_if_first_issue_is_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Result.<String, String>failed((String) null));
    }

    @Test
    void should_be_the_same_whatever_issue_orders() {
        Assertions.assertEquals(
                Result.succeeded(1, "a", "b"),
                Result.succeeded(1).withAddedIssues("a").withAddedIssues("b")
        );
    }

    @Test
    void should_map_content_when_present() {
        var result = Result.<Integer, String>succeeded(1)
                .map(Object::toString);

        Assertions.assertEquals("1", unwrapContent(result));
        Assertions.assertEquals(List.of(), result.issues());
    }

    @Test
    void should_not_call_mapper_when_content_is_missing() {
        var result = Result.<Integer, String>failed(List.of("failed"));

        var mappedResult = result.map(content -> {
            throw new RuntimeException();
        });

        Assertions.assertTrue(mappedResult.hasFailed());
        Assertions.assertNull(unwrapContent(mappedResult));
        Assertions.assertEquals(List.of("failed"), result.issues());
    }

    @Test
    void should_flatMap_content_when_present_collecting_issues() {
        var result = Result.<Integer, String>succeeded(2).withAddedIssues("A")
                .flatMap(content ->
                        Result.<Integer, String>succeeded(content * 3).withAddedIssues("B")
                );

        Assertions.assertEquals(6, unwrapContent(result));
        Assertions.assertEquals(List.of("A", "B"), result.issues());
    }

    @Test
    void should_not_call_flatMapper_when_content_is_missing() {
        var result = Result.<Integer, String>failed(List.of("failed"));

        var mappedResult = result.flatMap(content -> {
            throw new RuntimeException();
        });

        Assertions.assertTrue(mappedResult.hasFailed());
        Assertions.assertNull(unwrapContent(mappedResult));
        Assertions.assertEquals(List.of("failed"), result.issues());
    }

    @Test
    void should_not_call_mapper_when_issues_are_missing() {
        var result = Result.<Integer, String>succeeded(1);

        var mappedResult = result.mapIssues(issues -> {
            throw new RuntimeException();
        });

        Assertions.assertEquals(1, unwrapContent(mappedResult));
        Assertions.assertIterableEquals(List.of(), result.issues());
    }

    @Test
    void should_map_both_content_and_issues() {
        var result = Result.<Integer, String>succeeded(1).withAddedIssues("1", "2")
                .mapBoth(
                        Object::toString,
                        Integer::parseInt
                );

        Assertions.assertEquals("1", unwrapContent(result));
        Assertions.assertEquals(List.of(1, 2), result.issues());
    }

    @Test
    void should_map_both_no_content_and_issues() {
        var result = Result.<Integer, String>failed("1", "2")
                .mapBoth(
                        content -> {
                            throw new RuntimeException();
                        },
                        Integer::parseInt
                );

        Assertions.assertEquals(List.of(1, 2), result.issues());
    }

    @Test
    void should_add_issues_in_right_order() {
        var result1 = Result.<Integer, String>succeeded(1).withAddedIssues("1", "2").withAddedIssues(List.of("3", "4"));
        Assertions.assertEquals(List.of("1", "2", "3", "4"), result1.issues());

        var result2 = Result.<Integer, String>succeeded(1).withAddedIssues(List.of("1", "2")).withAddedIssues("3", "4");
        Assertions.assertEquals(List.of("1", "2", "3", "4"), result2.issues());
    }

    static class CustomException extends Exception {
        @Override
        public boolean equals(Object o) {
            return o instanceof CustomException;
        }
    }

    @Test
    void should_catch_exception_as_an_issue() {
        var result = Result.catching(CustomException.class)
                .run(
                        () -> {
                            throw new CustomException();
                        }
                );

        Assertions.assertNull(unwrapContent(result));
        Assertions.assertIterableEquals(List.of(new CustomException()), result.issues());
    }

    @Test
    void should_transparently_return_content_when_no_exception() {
        var result = Result.catching(CustomException.class)
                .run(() -> "A");

        Assertions.assertEquals("A", unwrapContent(result));
        Assertions.assertIterableEquals(List.of(), result.issues());
    }

    @Test
    void should_do_only_if_issues_exist() {
        final AtomicReference<List<String>> issueCollector = new AtomicReference<>();
        // With content, with issues
        var res = Result.<Integer, String>succeeded(1).withAddedIssues("a", "b")
                .ifIssuesExistDo(issueCollector::set)
                .thenGetContent();
        Assertions.assertIterableEquals(List.of("a", "b"), issueCollector.get());
        Assertions.assertEquals(1, res);

        // Without content, with issues, collecting issues in the second consumer
        issueCollector.set(List.of());
        Result.<Integer, String>succeeded(1).withAddedIssues("a", "b")
                .ifIssuesExistDo(issues -> {
                }) // do nothing here
                .ifSucceededDo((content, issues) -> issueCollector.set(issues));
        Assertions.assertIterableEquals(List.of("a", "b"), issueCollector.get());

        // Without content, with issues
        issueCollector.set(List.of());
        Result.<Integer, String>failed("a", "b")
                .ifIssuesExistDo(issueCollector::set)
                .ifSucceededDo(content -> {
                    throw new RuntimeException();
                });
        Assertions.assertIterableEquals(List.of("a", "b"), issueCollector.get());

        AtomicInteger atomicInteger = new AtomicInteger();
        // With content, without issues
        Result.<Integer, String>succeeded(1)
                .ifIssuesExistDo(issues -> {
                    throw new RuntimeException();
                })
                .ifSucceededDo(atomicInteger::set);
        Assertions.assertEquals(1, atomicInteger.get());
    }

    @Test
    void should_not_apply_consumer_if_has_succeeded() {
        // With content, without issues
        Result.<Integer, String>succeeded(1)
                .ifFailedDo(issues -> {
                    throw new RuntimeException();
                });
    }

    @Test
    void should_do_only_if_has_failed() {
        final AtomicReference<List<String>> issueCollector = new AtomicReference<>();
        var res = Result.<Integer, String>failed("a", "b")
                .ifFailedDo(issueCollector::set)
                .elseGetContent();
        Assertions.assertIterableEquals(List.of("a", "b"), issueCollector.get());
        Assertions.assertNull(res);

        res = Result.<Integer, String>succeeded(1).withAddedIssues("a", "b")
                .ifFailedDo(issues -> {
                    throw new RuntimeException();
                })
                .elseGetContent();
        Assertions.assertEquals(1, res);
    }

    @Test
    void should_transform_using_the_right_function_depending_if_succeeded() {
        var res = Result.succeeded("This is a success", 1, 2);

        var transformed = res.unwrap(
                (content, issues) -> {
                    Assertions.assertEquals("This is a success", content);
                    Assertions.assertEquals(List.of(1, 2), issues);
                    return 42d;
                },
                (issues) -> {
                    throw new RuntimeException("Shouldn't be called");
                }
        );

        Assertions.assertEquals(42d, transformed);
    }

    @Test
    void should_transform_using_the_right_function_depending_if_failed() {
        var res = Result.<String, Integer>failed( 1, 2);

        var transformed = res.unwrap(
                (content, issues) -> {
                    throw new RuntimeException("Shouldn't be called");
                },
                (issues) -> {
                    Assertions.assertEquals(List.of(1, 2), issues);
                    return 42d;
                }
        );

        Assertions.assertEquals(42d, transformed);
    }

    @Test
    void should_transform_issues_only_if_has_failed() {
        var res = Result.<Integer, String>failed("a", "b")
                .ifFailedTransform(issues -> String.join("", issues))
                .orElseUnwrap((content, issues) -> {
                    throw new RuntimeException();
                });
        Assertions.assertEquals("ab", res);

        final AtomicReference<List<String>> issueCollector = new AtomicReference<>();
        var unwrapped = Result.<Integer, String>succeeded(1).withAddedIssues("a", "b")
                .ifFailedTransform(issues -> {
                    throw new RuntimeException();
                })
                .orElseUnwrap((content, issues) -> {
                    issueCollector.set(issues);
                    return content;
                });
        Assertions.assertEquals(1, unwrapped);
        Assertions.assertIterableEquals(List.of("a", "b"), issueCollector.get());
    }

    @Test
    void should_transform_only_if_succeeded() {
        var transformed = Result.<Integer, String>succeeded(1).withAddedIssues("a", "b")
                .ifSucceededTransform((content, issues) -> content.toString())
                .orElse(issues -> "2");

        Assertions.assertEquals("1", transformed);

        var extractedIssues = Result.<Integer, String>succeeded(1).withAddedIssues("a", "b")
                .ifSucceededTransform((content, issues) -> issues)
                .orElse(issues -> List.of());

        Assertions.assertIterableEquals(extractedIssues, List.of("a", "b"));
    }

    @Test
    void should_combine_and_map() {
        var result1 = Result.<Integer, String>succeeded(4).withAddedIssues("a", "b");
        var result2 = Result.<Integer, String>succeeded(5).withAddedIssues("c", "d");

        var combinedResult = result1.combineWith(result2).mergeMap((i1, i2) -> i1 * i2);

        Assertions.assertEquals(20, unwrapContent(combinedResult));
        Assertions.assertEquals(List.of("a", "b", "c", "d"), combinedResult.issues());
    }

    @Test
    void should_combine_and_flatMap() {
        var result1 = Result.<Integer, String>succeeded(4).withAddedIssues("a", "b");
        var result2 = Result.<Integer, String>succeeded(5).withAddedIssues("c", "d");

        var combinedResult = result1.combineWith(result2)
                .mergeFlatMap((i1, i2) ->
                        Result.succeeded(i1 * i2, "e", "f")
                );

        Assertions.assertEquals(20, unwrapContent(combinedResult));
        Assertions.assertEquals(List.of("a", "b", "c", "d", "e", "f"), combinedResult.issues());
    }

    @Test
    void should_not_call_the_mapper_when_combining_with_failed() {
        var result1 = Result.<Integer, String>succeeded(4).withAddedIssues("a", "b");
        var result2 = Result.<Integer, String>failed("c");

        var combinedResult = result1.combineWith(result2)
                .mergeFlatMap((i1, i2) ->
                        Result.succeeded(i1 * i2, "e")
                );

        Assertions.assertNull(unwrapContent(combinedResult));
        Assertions.assertEquals(List.of("a", "b", "c"), combinedResult.issues());

        result1.combineWith(result2)
                .mergeFlatMap((i1, i2) ->
                        {
                            throw new RuntimeException();
                        }
                );
    }

    @Test
    void should_combine_all_successfull_including_all_the_issues() {
        var result1 = Result.<Integer, String>succeeded(4).withAddedIssues("a", "b");
        var result2 = Result.<Integer, String>failed("c");
        var result3 = Result.<Integer, String>succeeded(7).withAddedIssues("d", "e");

        var result = Result.combineAllSuccessful(List.of(result1, result2, result3));

        Assertions.assertEquals(List.of(4, 7), unwrapContent(result));
        Assertions.assertEquals(List.of("a", "b", "c", "d", "e"), result.issues());
    }

    private static <T, I> T unwrapContent(Result<T, I> result) {
        return result.ifSucceededTransform((content, issues) -> content).orElse(issues -> null);
    }
}