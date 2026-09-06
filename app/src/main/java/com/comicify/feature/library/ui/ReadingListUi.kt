package com.comicify.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.core.ui.SectionHeader
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibrarySort
import com.comicify.feature.library.domain.ReadingList
import com.comicify.feature.library.domain.ReadingListOrder

private val ListEmptyPadding = 32.dp
private val DialogBodyMaxHeight = 320.dp
private val CheckboxRowGap = 4.dp

data class ReadingListActions(
    val open: (ReadingList?) -> Unit,
    val create: (String, LibraryComic?) -> Unit,
    val rename: (ReadingList, String) -> Unit,
    val delete: (ReadingList) -> Unit,
    val restore: (ReadingList) -> Unit,
    val toggle: (ReadingList, LibraryComic) -> Unit,
    val remove: (ReadingList, LibraryComic) -> Unit,
    val undoRemove: () -> Unit,
    val move: (ReadingList, LibraryComic, Boolean) -> Unit,
)

internal data class ReadingListsUi(
    val lists: List<ReadingList>,
    val opened: ReadingList?,
    val actions: ReadingListActions,
)

@Composable
internal fun ShelfTitle(
    state: LibraryUiState,
    lists: ReadingListsUi,
    onSortSelected: (LibrarySort) -> Unit,
) {
    var switcherOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val opened = lists.opened
    Box {
        SectionHeader(
            eyebrow = opened?.let { listEyebrow(state.allComics, it) } ?: stringResource(R.string.library_shelf_eyebrow),
            title = opened?.name ?: stringResource(R.string.library_lists_all),
            onTitleClick = { switcherOpen = true },
        ) {
            if (opened == null) RecentSortToggle(sort = state.sort, onSortSelected = onSortSelected)
            else ListMenu(list = opened, onRename = { renaming = true }, onDelete = { lists.actions.delete(it) })
        }
        ListSwitcher(
            expanded = switcherOpen,
            lists = lists,
            onDismiss = { switcherOpen = false },
            onNewList = { switcherOpen = false; creating = true },
        )
    }
    if (creating) {
        ListNameDialog(
            title = stringResource(R.string.library_list_new),
            confirmLabel = stringResource(R.string.library_list_create),
            initialName = "",
            onConfirm = { lists.actions.create(it, null) },
            onDismiss = { creating = false },
        )
    }
    if (renaming && opened != null) {
        ListNameDialog(
            title = stringResource(R.string.library_list_rename),
            confirmLabel = stringResource(R.string.library_list_save),
            initialName = opened.name,
            onConfirm = { lists.actions.rename(opened, it) },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun listEyebrow(comics: List<LibraryComic>, list: ReadingList): String {
    val members = LibraryCatalog.inList(comics, list)
    val read = stringResource(R.string.library_group_read, members.count { it.completed }, members.size)
    return stringResource(R.string.library_series_publisher_read, stringResource(R.string.library_list_eyebrow), read)
}

@Composable
private fun ListSwitcher(
    expanded: Boolean,
    lists: ReadingListsUi,
    onDismiss: () -> Unit,
    onNewList: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = MenuHeaderMaxWidth),
    ) {
        SwitcherRow(
            name = stringResource(R.string.library_lists_all),
            selected = lists.opened == null,
            onClick = { onDismiss(); lists.actions.open(null) },
        )
        if (lists.lists.isNotEmpty()) HorizontalDivider(color = CardLine)
        lists.lists.forEach { list ->
            SwitcherRow(
                name = list.name,
                selected = list.id == lists.opened?.id,
                count = list.comicIds.size,
                onClick = { onDismiss(); lists.actions.open(list) },
            )
        }
        HorizontalDivider(color = CardLine)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_list_new)) },
            leadingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
            onClick = onNewList,
        )
    }
}

@Composable
private fun SwitcherRow(name: String, selected: Boolean, count: Int? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            if (selected) Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Accent)
        },
        trailingIcon = {
            count?.let {
                Text(
                    text = pluralStringResource(R.plurals.library_count, it, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun ListMenu(list: ReadingList, onRename: () -> Unit, onDelete: (ReadingList) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GhostAction(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.library_list_menu),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuHeader(title = list.name)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_list_rename)) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_list_delete), color = Danger) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = Danger) },
                onClick = { expanded = false; onDelete(list) },
            )
        }
    }
}

@Composable
internal fun ReadingListMenuItems(
    list: ReadingList,
    comic: LibraryComic,
    actions: ReadingListActions,
    onDismiss: () -> Unit,
) {
    if (ReadingListOrder.canMoveUp(list.comicIds, comic.id)) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_list_move_up)) },
            leadingIcon = { Icon(imageVector = Icons.Filled.ArrowUpward, contentDescription = null) },
            onClick = { onDismiss(); actions.move(list, comic, true) },
        )
    }
    if (ReadingListOrder.canMoveDown(list.comicIds, comic.id)) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_list_move_down)) },
            leadingIcon = { Icon(imageVector = Icons.Filled.ArrowDownward, contentDescription = null) },
            onClick = { onDismiss(); actions.move(list, comic, false) },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.library_list_remove)) },
        leadingIcon = { Icon(imageVector = Icons.Outlined.RemoveCircleOutline, contentDescription = null) },
        onClick = { onDismiss(); actions.remove(list, comic) },
    )
}

@Composable
internal fun AddToListMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.library_list_add_to)) },
        leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
internal fun AddToListDialog(comic: LibraryComic, lists: ReadingListsUi, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(lists.lists.isEmpty()) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = comic.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            if (creating) {
                ListNameField(name = name, onNameChanged = { name = it })
            } else {
                ListCheckboxes(
                    comic = comic,
                    lists = lists,
                    onNewList = { creating = true },
                )
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { lists.actions.create(name, comic); onDismiss() },
                ) {
                    Text(stringResource(R.string.library_list_create))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_end_close)) }
            }
        },
        dismissButton = {
            if (creating) TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_delete_cancel)) }
        },
    )
}

@Composable
private fun ListCheckboxes(comic: LibraryComic, lists: ReadingListsUi, onNewList: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = DialogBodyMaxHeight).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CheckboxRowGap),
    ) {
        lists.lists.forEach { list ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = comic.id in list.comicIds,
                    onCheckedChange = { lists.actions.toggle(list, comic) },
                )
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onNewList) { Text(stringResource(R.string.library_list_new)) }
    }
}

@Composable
private fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { ListNameField(name = name, onNameChanged = { name = it }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name); onDismiss() }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_delete_cancel)) }
        },
    )
}

@Composable
private fun ListNameField(name: String, onNameChanged: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChanged,
        singleLine = true,
        label = { Text(stringResource(R.string.library_list_name_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ListEmpty() {
    Text(
        text = stringResource(R.string.library_list_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = InkFaint,
        modifier = Modifier.fillMaxWidth().padding(vertical = ListEmptyPadding),
        textAlign = TextAlign.Center,
    )
}
