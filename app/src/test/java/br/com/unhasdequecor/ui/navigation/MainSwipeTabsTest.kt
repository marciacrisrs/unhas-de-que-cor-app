package br.com.unhasdequecor.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainSwipeTabsTest {

    @Test
    fun routes_areLeftToRightBottomBarOrderWithoutFab() {
        assertThat(MainSwipeTabs.routes).containsExactly(
            Routes.HOME,
            Routes.HISTORY,
            Routes.FAVORITES,
            Routes.PROFILE,
        ).inOrder()
        assertThat(MainSwipeTabs.routes).doesNotContain(Routes.CONTEXT)
    }

    @Test
    fun indexOf_knownAndUnknown() {
        assertThat(MainSwipeTabs.indexOf(Routes.FAVORITES)).isEqualTo(2)
        assertThat(MainSwipeTabs.indexOf(Routes.CONTEXT)).isNull()
        assertThat(MainSwipeTabs.indexOf(null)).isNull()
    }

    @Test
    fun routeAt_clampsToHome() {
        assertThat(MainSwipeTabs.routeAt(0)).isEqualTo(Routes.HOME)
        assertThat(MainSwipeTabs.routeAt(99)).isEqualTo(Routes.HOME)
    }

    @Test
    fun contains_onlySwipeTabs() {
        assertThat(MainSwipeTabs.contains(Routes.HOME)).isTrue()
        assertThat(MainSwipeTabs.contains(Routes.CONTEXT)).isFalse()
        assertThat(MainSwipeTabs.contains(null)).isFalse()
    }
}
