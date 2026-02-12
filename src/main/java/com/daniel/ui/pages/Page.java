package com.daniel.ui.pages;

import javafx.scene.Parent;

public interface Page {
    Parent view();
    default void onShow() {}
}
