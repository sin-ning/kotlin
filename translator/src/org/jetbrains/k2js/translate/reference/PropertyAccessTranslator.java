package org.jetbrains.k2js.translate.reference;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.util.AstUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyGetterDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertySetterDescriptor;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetQualifiedExpression;
import org.jetbrains.jet.lang.psi.JetSimpleNameExpression;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;

import java.util.Arrays;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getDescriptorForReferenceExpression;
import static org.jetbrains.k2js.translate.utils.PsiUtils.getSelectorAsSimpleName;
import static org.jetbrains.k2js.translate.utils.PsiUtils.isBackingFieldReference;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.backingFieldReference;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.getImplicitReceiver;

/**
 * @author Pavel Talanov
 */
public final class PropertyAccessTranslator extends AccessTranslator {

    private static final String MESSAGE = "Cannot be accessor call. Use canBeProperty*Call to ensure this method " +
            "can be called safely.";

    @NotNull
    private static PropertyDescriptor getPropertyDescriptor(@NotNull JetSimpleNameExpression expression,
                                                            @NotNull TranslationContext context) {
        DeclarationDescriptor descriptor =
                getDescriptorForReferenceExpression(context.bindingContext(), expression);
        assert descriptor instanceof PropertyDescriptor : "Must be a property descriptor.";
        return (PropertyDescriptor) descriptor;
    }

    @NotNull
    public static JsExpression translateAsPropertyGetterCall(@NotNull PropertyDescriptor descriptor,
                                                             @NotNull TranslationContext context) {
        return (newInstance(descriptor, context))
                .translateAsGet();
    }

    @NotNull
    public static JsExpression translateAsPropertyGetterCall(@NotNull JetSimpleNameExpression expression,
                                                             @NotNull TranslationContext context) {
        return (newInstance(expression, context))
                .translateAsGet();
    }

    @NotNull
    private static PropertyAccessTranslator newInstance(@NotNull PropertyDescriptor descriptor,
                                                        @NotNull TranslationContext context) {
        return new PropertyAccessTranslator(descriptor, null, false, context);
    }

    @NotNull
    public static PropertyAccessTranslator newInstance(@NotNull JetQualifiedExpression expression,
                                                       @NotNull TranslationContext context) {
        JetExpression qualifier = expression.getReceiverExpression();
        JetSimpleNameExpression selector = getNotNullSelector(expression);
        PropertyDescriptor propertyDescriptor = getPropertyDescriptor(selector, context);
        boolean isBackingFieldAccess = isBackingFieldReference(selector);
        return new PropertyAccessTranslator(propertyDescriptor, qualifier, isBackingFieldAccess, context);
    }

    @NotNull
    public static PropertyAccessTranslator newInstance(@NotNull JetSimpleNameExpression expression,
                                                       @NotNull TranslationContext context) {
        PropertyDescriptor propertyDescriptor = getPropertyDescriptor(expression, context);
        return new PropertyAccessTranslator(propertyDescriptor, null, isBackingFieldReference(expression), context);
    }


    @NotNull
    public static PropertyAccessTranslator newInstance(@NotNull JetExpression expression,
                                                       @NotNull TranslationContext context) {
        if (expression instanceof JetQualifiedExpression) {
            return newInstance((JetQualifiedExpression) expression, context);
        }
        if (expression instanceof JetSimpleNameExpression) {
            return newInstance((JetSimpleNameExpression) expression, context);
        }
        throw new AssertionError(MESSAGE);
    }

    public static boolean canBePropertyGetterCall(@NotNull JetQualifiedExpression expression,
                                                  @NotNull TranslationContext context) {
        JetSimpleNameExpression selector = getSelectorAsSimpleName(expression);
        if (selector == null) {
            return false;
        }
        return canBePropertyGetterCall(selector, context);
    }

    public static boolean canBePropertyGetterCall(@NotNull JetSimpleNameExpression expression,
                                                  @NotNull TranslationContext context) {
        return (getDescriptorForReferenceExpression
                (context.bindingContext(), expression) instanceof PropertyDescriptor);
    }

    public static boolean canBePropertyGetterCall(@NotNull JetExpression expression,
                                                  @NotNull TranslationContext context) {
        if (expression instanceof JetQualifiedExpression) {
            return canBePropertyGetterCall((JetQualifiedExpression) expression, context);
        }
        if (expression instanceof JetSimpleNameExpression) {
            return canBePropertyGetterCall((JetSimpleNameExpression) expression, context);
        }
        return false;
    }

    public static boolean canBePropertyAccess(@NotNull JetExpression expression,
                                              @NotNull TranslationContext context) {
        return canBePropertyGetterCall(expression, context);
    }

    @Nullable
    private final JetExpression qualifier;
    @NotNull
    private final PropertyDescriptor propertyDescriptor;
    private final boolean isBackingFieldAccess;

    private PropertyAccessTranslator(@NotNull PropertyDescriptor descriptor,
                                     @Nullable JetExpression qualifier,
                                     boolean isBackingFieldAccess,
                                     @NotNull TranslationContext context) {
        super(context);
        this.qualifier = qualifier;
        this.propertyDescriptor = descriptor.getOriginal();
        this.isBackingFieldAccess = isBackingFieldAccess;
    }

    @Override
    @NotNull
    public JsExpression translateAsGet() {
        if (isBackingFieldAccess) {
            return backingFieldGet();
        } else {
            return getterCall();
        }
    }

    @NotNull
    private JsExpression backingFieldGet() {
        return backingFieldReference(context(), propertyDescriptor);
    }

    @NotNull
    private JsExpression getterCall() {
        return CallTranslator.translate(translateQualifier(), getGetterDescriptor(), context());
    }

    @Override
    @NotNull
    public JsExpression translateAsSet(@NotNull JsExpression toSetTo) {
        if (isBackingFieldAccess) {
            return backingFieldAssignment(toSetTo);
        } else {
            return setterCall(toSetTo);
        }
    }

    @NotNull
    private JsExpression setterCall(@NotNull JsExpression toSetTo) {
        return CallTranslator.translate(translateQualifier(), Arrays.asList(toSetTo), getSetterDescriptor(), context());
    }

    @NotNull
    private JsExpression backingFieldAssignment(@NotNull JsExpression toSetTo) {
        JsNameRef backingFieldReference = backingFieldReference(context(), propertyDescriptor);
        return AstUtil.newAssignment(backingFieldReference, toSetTo);
    }

    @NotNull
    private JsExpression translateQualifier() {
        if (qualifier != null) {
            return Translation.translateAsExpression(qualifier, context());
        }
        JsExpression implicitReceiver = getImplicitReceiver(context(), propertyDescriptor);
        assert implicitReceiver != null : "Property can only be a member of class or a namespace.";
        return implicitReceiver;
    }

    @NotNull
    private static JetSimpleNameExpression getNotNullSelector(@NotNull JetQualifiedExpression qualifiedExpression) {
        JetSimpleNameExpression selectorExpression = getSelectorAsSimpleName(qualifiedExpression);
        assert selectorExpression != null : MESSAGE;
        return selectorExpression;
    }

    @NotNull
    private PropertyGetterDescriptor getGetterDescriptor() {
        PropertyGetterDescriptor getter = propertyDescriptor.getGetter();
        assert getter != null : propertyDescriptor.getName() + " does not have a getter.";
        return getter;
    }

    @NotNull
    private PropertySetterDescriptor getSetterDescriptor() {
        PropertySetterDescriptor setter = propertyDescriptor.getSetter();
        assert setter != null;
        return setter;
    }

}
