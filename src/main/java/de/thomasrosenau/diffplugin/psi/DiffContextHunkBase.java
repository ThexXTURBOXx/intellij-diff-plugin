/*
 This file has been changed by Nico Mexis under the terms of the Apache-2.0 license.
 Original code is by Thomas Rosenau.

 Copyright 2020 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class DiffContextHunkBase extends DiffNavigationItem {
    public DiffContextHunkBase(@NotNull ASTNode node) {
        super(node);
    }

    public String getPlaceholderText() {
        PsiElement fromNode = ((DiffContextHunk) this).getContextHunkFrom().getFirstChild();
        String fromText = fromNode.getText();
        PsiElement toNode = ((DiffContextHunk) this).getContextHunkTo().getFirstChild();
        String toText = toNode.getText();
        return "@@ -" + fromText.substring(4, fromText.length() - 5) + " +" + toText.substring(4, toText.length() - 5) +
               " @@";
    }
}
