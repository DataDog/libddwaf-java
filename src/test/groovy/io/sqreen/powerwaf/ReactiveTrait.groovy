package io.sqreen.powerwaf

import org.junit.After

trait ReactiveTrait extends PowerwafTrait {

    Additive additive

    @After
    void clearAdditive() {
        additive?.close()
    }
}
