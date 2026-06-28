package com.example.splitreader.presentation.premium

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.splitreader.R
import com.example.splitreader.data.billing.PurchaseEvent

/**
 * Collects [PremiumViewModel.events] and surfaces each as a toast. Drop this into any screen that
 * can start a purchase/restore so the user gets feedback regardless of which screen they're on.
 */
@Composable
fun PurchaseEventEffect(viewModel: PremiumViewModel) {
    val context = LocalContext.current
    val success = stringResource(R.string.purchase_success)
    val pending = stringResource(R.string.purchase_pending)
    val nothing = stringResource(R.string.restore_none)
    val notReady = stringResource(R.string.purchase_store_not_ready)
    val failed = stringResource(R.string.purchase_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                PurchaseEvent.Success -> success
                PurchaseEvent.Pending -> pending
                PurchaseEvent.NothingToRestore -> nothing
                PurchaseEvent.StoreNotReady -> notReady
                is PurchaseEvent.Failed -> failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
