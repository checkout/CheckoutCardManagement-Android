package com.checkout.cardmanagement.model

import androidx.compose.ui.text.TextStyle
import com.checkout.cardnetwork.common.model.PanViewConfiguration
import com.checkout.cardnetwork.common.model.PinViewConfiguration
import com.checkout.cardnetwork.common.model.SecurityCodeViewConfiguration

/**
 * Collection of properties enabling the customisation of UI outputs from the framework
 *
 * @property pinTextStyle [TextStyle] used when returning UI component with the pin number
 * @property panTextStyle [TextStyle] used when returning UI component with the long card number
 * @property securityCodeTextStyle [TextStyle] used when returning UI component with the security code
 * @property panTextSeparator Text separator used to format the card number when displayed. Default is single space
 */
public data class CardManagementDesignSystem(
    private val pinTextStyle: TextStyle,
    private val panTextStyle: TextStyle,
    private val securityCodeTextStyle: TextStyle,
    internal val panTextSeparator: String = " ",
) {
    public constructor(textStyle: TextStyle, panTextSeparator: String = " ") : this(
        pinTextStyle = textStyle,
        panTextStyle = textStyle,
        securityCodeTextStyle = textStyle,
        panTextSeparator = panTextSeparator,
    )

    internal val panViewConfig by lazy {
        PanViewConfiguration(panTextStyle, panTextSeparator)
    }

    internal val pinViewConfig by lazy {
        PinViewConfiguration(pinTextStyle)
    }

    internal val securityCodeViewConfig by lazy {
        SecurityCodeViewConfiguration(securityCodeTextStyle)
    }
}
