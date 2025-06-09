/*
 * Created by nphau on 11/19/22, 4:16 PM
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 11/19/22, 3:58 PM
 */

package com.manuelost.app.omldatatransfer.data.models

import com.manuelost.app.omldatatransfer.data.Database
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Cart(val id: String, val items: List<Fruit>) {

    companion object {
        fun sample(): Cart {
            return Cart(
                id = UUID.randomUUID().toString(),
                items = Database.FRUITS
            )
        }
    }
}