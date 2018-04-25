package com.fsck.k9.activity.compose

import com.fsck.k9.autocrypt.AutocryptDraftStateHeaderParser
import org.koin.dsl.module.applicationContext


val composeModule = applicationContext {
    bean { AutocryptDraftStateHeaderParser() }
}