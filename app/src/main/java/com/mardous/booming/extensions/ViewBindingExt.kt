/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mardous.booming.extensions

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A [ReadOnlyProperty] that lazily creates a [ViewBinding] from the Fragment's view and
 * automatically clears the reference when the view is destroyed, removing the need for the
 * `_binding`/`onDestroyView` boilerplate.
 *
 * Access to [binding][getValue] after the view is destroyed is invalid (it throws), mirroring the
 * previous non-null `binding` accessor. Callbacks that may run after the view is gone should guard
 * with `if (view == null) return` before touching the binding.
 *
 * @author Christians M. A. (mardous)
 */
class FragmentViewBindingDelegate<T : ViewBinding>(
    private val fragment: Fragment,
    private val viewBindingFactory: (View) -> T
) : ReadOnlyProperty<Fragment, T> {

    private var binding: T? = null

    init {
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            private val viewLifecycleObserver = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    binding = null
                }
            }

            override fun onCreate(owner: LifecycleOwner) {
                fragment.viewLifecycleOwnerLiveData.observe(fragment) { viewLifecycleOwner ->
                    viewLifecycleOwner?.lifecycle?.addObserver(viewLifecycleObserver)
                }
            }
        })
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        binding?.let { return it }

        val lifecycle = thisRef.viewLifecycleOwner.lifecycle
        check(lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            "Should not attempt to get bindings when the Fragment's view has been destroyed."
        }
        return viewBindingFactory(thisRef.requireView()).also { binding = it }
    }
}

/**
 * Binds a [ViewBinding] to this Fragment's view lifecycle.
 *
 * Usage:
 * ```
 * private val binding by viewBinding(FragmentExampleBinding::bind)
 * ```
 */
fun <T : ViewBinding> Fragment.viewBinding(viewBindingFactory: (View) -> T) =
    FragmentViewBindingDelegate(this, viewBindingFactory)
