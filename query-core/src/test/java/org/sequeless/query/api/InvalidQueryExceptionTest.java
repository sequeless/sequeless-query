package org.sequeless.query.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidQueryExceptionTest {

    @Test
    void messageJoinsProblemsWithSemicolons() {
        InvalidQueryException exception =
                new InvalidQueryException(List.of("unknown field 'x'", "sort path 'y' is not sortable"));

        assertThat(exception).hasMessage("invalid query: unknown field 'x'; sort path 'y' is not sortable");
        assertThat(exception.problems()).containsExactly("unknown field 'x'", "sort path 'y' is not sortable");
    }

    @Test
    void problemsAreCopiedAndUnmodifiable() {
        List<String> source = new ArrayList<>(List.of("one"));
        InvalidQueryException exception = new InvalidQueryException(source);
        source.clear();

        assertThat(exception.problems()).containsExactly("one");
        assertThatThrownBy(() -> exception.problems().add("two")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullProblems() {
        assertThatNullPointerException().isThrownBy(() -> new InvalidQueryException(null));
    }
}
