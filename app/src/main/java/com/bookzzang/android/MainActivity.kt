package com.bookzzang.android

import android.os.Bundle
import android.app.Application
import android.app.Activity
import android.widget.Toast
import android.app.DatePickerDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.bookzzang.android.data.BookSummary
import com.bookzzang.android.data.BookzzangApi
import com.bookzzang.android.data.ReadingStatus
import com.bookzzang.android.data.BookzzangAuthClient
import com.bookzzang.android.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = lightColorScheme()) { BookzzangApp() } } }
}

private val Plum = Color(0xFF5B3B79)
private val SoftPlum = Color(0xFFF2ECF7)
private val Ink = Color(0xFF251E28)

@Composable
private fun BookCover(book: BookSummary, modifier: Modifier, contentScale: ContentScale) {
    SubcomposeAsyncImage(
        model = book.coverUrl,
        contentDescription = "${book.title} 표지",
        contentScale = contentScale,
        modifier = modifier.background(SoftPlum, RoundedCornerShape(8.dp)),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Plum)
            }
        },
        error = {
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = Plum)
                    Text("표지 없음", style = MaterialTheme.typography.labelSmall, color = Plum)
                }
            }
        },
        success = { SubcomposeAsyncImageContent() }
    )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = BookzzangApi(); private val auth = BookzzangAuthClient()
    private val sessionStore = SessionStore(application)
    var query by mutableStateOf(""); var books by mutableStateOf<List<BookSummary>>(emptyList()); var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false); var accessToken by mutableStateOf<String?>(null); var screen by mutableStateOf("search"); var previousScreen by mutableStateOf<String?>(null)
    var shelf by mutableStateOf<List<BookSummary>>(emptyList())
    var reorderingShelf by mutableStateOf(false)
    private var shelfBeforeReorder: List<BookSummary>? = null
    var selectedShelfBook by mutableStateOf<BookSummary?>(null)
    var selectedSearchBook by mutableStateOf<BookSummary?>(null)
    var emailAvailable by mutableStateOf<Boolean?>(null)
    var emailAvailabilityMessage by mutableStateOf<String?>(null)
    var emailAvailabilityError by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    private var loginDestination = "shelf"

    init { sessionStore.read()?.let { saved -> viewModelScope.launch { runCatching { withContext(Dispatchers.IO) {
        val refreshed = auth.refresh(saved.refreshToken); refreshed to api.loadShelf(refreshed.accessToken)
    } }
        .onSuccess { (refreshed, savedShelf) -> accessToken = refreshed.accessToken; sessionStore.save(refreshed); shelf = savedShelf; screen = "shelf" }
        .onFailure { sessionStore.clear() } } } }

    fun search() = viewModelScope.launch { loading = true; error = null; runCatching { withContext(Dispatchers.IO) { api.search(query) } }
        .onSuccess { results -> books = results.distinctBy { book -> "${book.title.trim()}|${book.authors.joinToString().trim()}" } }
        .onFailure { error = it.message }; loading = false }
    fun login(email: String, password: String, rememberSession: Boolean) = viewModelScope.launch {
        if (email.isBlank() || password.isBlank()) { error = "이메일과 비밀번호를 입력해 주세요."; return@launch }
        loading = true; error = null; runCatching { withContext(Dispatchers.IO) {
            val session = auth.signIn(email, password); session to api.loadShelf(session.accessToken)
        } }
        .onSuccess { (session, savedShelf) -> accessToken = session.accessToken; shelf = savedShelf; if (rememberSession) sessionStore.save(session) else sessionStore.clear(); screen = loginDestination; loginDestination = "shelf" }.onFailure { error = it.message }; loading = false }
    fun checkEmailAvailability(email: String) = viewModelScope.launch {
        emailAvailable = null; emailAvailabilityMessage = null; emailAvailabilityError = false
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailAvailabilityMessage = "올바른 이메일 주소를 입력해 주세요."
            emailAvailabilityError = true
            return@launch
        }
        loading = true; error = null
        runCatching { withContext(Dispatchers.IO) { api.isEmailAvailable(email) } }
            .onSuccess { available ->
                emailAvailable = available
                emailAvailabilityMessage = if (available) "사용 가능한 이메일입니다." else "이미 가입된 이메일입니다."
                emailAvailabilityError = !available
            }
            .onFailure {
                emailAvailabilityMessage = it.message ?: "이메일 중복 확인에 실패했습니다."
                emailAvailabilityError = true
            }
        loading = false
    }
    fun signUp(email: String, password: String, passwordConfirmation: String, nickname: String, gender: String?, ageGroup: Int?) = viewModelScope.launch {
        if (emailAvailable != true) { error = "이메일 중복 확인을 먼저 해 주세요."; return@launch }
        if (password.length < 6) { error = "비밀번호는 6자 이상으로 입력해 주세요."; return@launch }
        if (password != passwordConfirmation) { error = "비밀번호가 일치하지 않습니다."; return@launch }
        if (nickname.trim().length !in 2..20) { error = "닉네임은 2~20자로 입력해 주세요."; return@launch }
        loading = true; error = null
        runCatching { withContext(Dispatchers.IO) { api.signUp(email, password, nickname.trim(), gender, ageGroup) } }
            .onSuccess { emailAvailable = null; notice = "회원가입이 완료되었습니다. 이메일과 비밀번호로 로그인해 주세요."; screen = "login" }
            .onFailure { error = it.message }; loading = false
    }
    fun logout() {
        sessionStore.read()?.let { saved -> viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { auth.logout(saved.refreshToken) } } } }
        sessionStore.clear(); accessToken = null; shelf = emptyList(); screen = "search"; notice = "로그아웃되었습니다."
    }
    fun add(book: BookSummary, status: ReadingStatus, favorite: Boolean = false, rating: Double? = null,
            reviewText: String? = null, startedOn: String? = null, finishedOn: String? = null) = viewModelScope.launch {
        val isbn = book.isbn13 ?: run { error = "ISBN이 없는 도서는 아직 등록할 수 없습니다."; return@launch }
        val token = accessToken ?: run { openLogin(); return@launch }; loading = true
        if (!validOptionalDate(startedOn) || !validOptionalDate(finishedOn)) {
            loading = false; error = "날짜는 2026-08-12 형식으로 입력해 주세요."; return@launch
        }
        runCatching { withContext(Dispatchers.IO) {
            api.registerBook(token, isbn, status, favorite, rating, reviewText, startedOn, finishedOn)
            api.loadShelf(token)
        } }
            .onSuccess { savedShelf -> shelf = savedShelf; screen = "shelf" }
            .onFailure { error = it.message }; loading = false
    }
    private fun validOptionalDate(value: String?): Boolean = value.isNullOrBlank() || try {
        LocalDate.parse(value); true
    } catch (_: DateTimeParseException) { false }
    fun openDetail(index: Int) {
        val book = books.getOrNull(index) ?: return
        previousScreen = screen
        selectedSearchBook = book
        screen = "searchDetail"
        val isbn = book.isbn13 ?: return
        viewModelScope.launch {
            loading = true
            runCatching { withContext(Dispatchers.IO) { api.findBook(isbn) } }
                .onSuccess { detailed ->
                    selectedSearchBook = book.copy(
                        pageCount = detailed.pageCount,
                        thicknessMm = detailed.thicknessMm,
                        coverUrl = book.coverUrl ?: detailed.coverUrl
                    )
                }
                .onFailure { error = "도서 상세 정보를 불러오지 못했습니다." }
            loading = false
        }
    }
    fun openShelfDetail(book: BookSummary) { selectedShelfBook = book; previousScreen = screen; screen = "shelfDetail" }
    fun openLogin(destination: String = "shelf") { previousScreen = screen; loginDestination = destination; screen = "login" }
    fun openSignUp() { previousScreen = screen; error = null; emailAvailable = null; emailAvailabilityMessage = null; emailAvailabilityError = false; screen = "signup" }
    fun openTopLevel(destination: String) {
        error = null; notice = null
        if (destination in setOf("shelf", "records") && accessToken == null) openLogin(destination) else screen = destination
    }
    fun beginShelfReorder() {
        if (shelfBeforeReorder == null) shelfBeforeReorder = shelf
    }
    fun moveShelfBook(book: BookSummary, targetIndex: Int) {
        val fromIndex = shelf.indexOfFirst { it.isbn13 == book.isbn13 }
        if (fromIndex < 0) return
        val boundedTarget = targetIndex.coerceIn(0, shelf.lastIndex)
        if (fromIndex == boundedTarget) return
        shelf = shelf.toMutableList().apply { add(boundedTarget, removeAt(fromIndex)) }
    }
    fun finishShelfReorder() {
        val previous = shelfBeforeReorder ?: return
        val token = accessToken ?: run { shelf = previous; shelfBeforeReorder = null; return }
        val reordered = shelf
        shelfBeforeReorder = null
        if (previous.map { it.isbn13 } == reordered.map { it.isbn13 }) return
        viewModelScope.launch {
            reorderingShelf = true; error = null
            runCatching { withContext(Dispatchers.IO) { api.reorderShelf(token, reordered) } }
                .onSuccess { notice = "책 순서를 저장했습니다." }
                .onFailure { shelf = previous; error = "책 순서를 저장하지 못해 이전 순서로 되돌렸습니다." }
            reorderingShelf = false
        }
    }
    fun cancelShelfReorder() {
        shelfBeforeReorder?.let { shelf = it }
        shelfBeforeReorder = null
    }
    fun goBack(): Boolean = when {
        screen == "search" -> false
        screen == "login" || screen == "signup" -> { screen = previousScreen ?: "search"; previousScreen = null; true }
        screen == "searchDetail" -> { selectedSearchBook = null; screen = "search"; true }
        screen == "shelfDetail" -> { screen = previousScreen ?: "shelf"; previousScreen = null; true }
        screen == "shelf" || screen == "records" || screen == "profile" -> { screen = "search"; true }
        else -> { screen = "search"; true }
    }
}

@Composable private fun BookzzangApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var lastBackPressedAt by remember { mutableStateOf(0L) }
    BackHandler {
        if (!vm.goBack()) {
            val now = System.currentTimeMillis()
            if (now - lastBackPressedAt < 2_000) (context as? Activity)?.finish()
            else { lastBackPressedAt = now; Toast.makeText(context, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show() }
        }
    }
    val showBottomNavigation = vm.screen in setOf("search", "shelf", "records", "profile")
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFFFBFE)) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { if (showBottomNavigation) BookzzangBottomNavigation(vm) }
    ) { padding -> Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SoftPlum), shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.bookzzang_icon), contentDescription = "책짱 아이콘", contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f)) { Text("책짱", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("나만의 독서 책장", color = Plum, style = MaterialTheme.typography.bodySmall) }
                Text(if (vm.accessToken == null) "비회원 탐색" else "내 서재", color = Plum, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(16.dp)); vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }; vm.notice?.let { Text(it, color = Plum); Spacer(Modifier.height(8.dp)) }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                vm.screen == "login" -> LoginScreen(vm)
                vm.screen == "signup" -> SignUpScreen(vm)
                vm.screen == "shelf" -> ShelfScreen(vm)
                vm.screen == "records" -> RecordsScreen(vm)
                vm.screen == "profile" -> ProfileScreen(vm)
                vm.screen == "shelfDetail" -> vm.selectedShelfBook?.let { DetailScreen(vm, it) }
                vm.screen == "searchDetail" -> vm.selectedSearchBook?.let { DetailScreen(vm, it) }
                else -> SearchScreen(vm)
            }
        }
    } }
}
}

private data class BottomDestination(
    val screen: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable private fun BookzzangBottomNavigation(vm: MainViewModel) {
    val destinations = listOf(
        BottomDestination("search", "탐색", Icons.Outlined.Search),
        BottomDestination("shelf", "내 책장", Icons.Outlined.AutoStories),
        BottomDestination("records", "기록", Icons.Outlined.EditNote),
        BottomDestination("profile", "마이", Icons.Outlined.AccountCircle)
    )
    NavigationBar(containerColor = Color.White, tonalElevation = 3.dp) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = vm.screen == destination.screen,
                onClick = { vm.openTopLevel(destination.screen) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable private fun SearchScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            label = { Text("책 제목, 저자") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (!vm.loading && vm.query.isNotBlank()) vm.search() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp)); Button({ vm.search() }, enabled = !vm.loading && vm.query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("도서 검색") }
        if (vm.loading) CircularProgressIndicator(Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(vm.books, key = { _, book -> book.isbn13 ?: "${book.title}|${book.authors.joinToString()}" }) { index, book ->
                Card(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { vm.openDetail(index) }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(14.dp)) { BookCover(book, Modifier.width(72.dp).height(104.dp), ContentScale.Crop); Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(book.title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(book.authors.joinToString(), color = Plum); Spacer(Modifier.height(4.dp)); Text(listOfNotNull(book.publisher, book.pageCount?.let { "${it}쪽" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color(0xFF766B78)) } } }
            }
        }
    }
}

@Composable private fun DetailScreen(vm: MainViewModel, book: BookSummary) {
    var status by remember { mutableStateOf(book.readingStatus ?: ReadingStatus.WANT_TO_READ) }
    var favorite by remember { mutableStateOf(book.favorite) }
    var rating by remember { mutableStateOf(book.rating) }
    var review by remember { mutableStateOf(book.reviewText.orEmpty()) }
    var startedOn by remember { mutableStateOf(book.startedOn.orEmpty()) }
    var finishedOn by remember { mutableStateOf(book.finishedOn.orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        Text(if (book.readingStatus == null) "책 등록" else "독서 기록 수정", color = Plum, style = MaterialTheme.typography.labelLarge)
        BookCover(book, Modifier.fillMaxWidth().height(220.dp).padding(vertical = 10.dp), ContentScale.Fit)
        Spacer(Modifier.height(6.dp))
        Text(book.title, color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (book.authors.isNotEmpty()) Text(book.authors.joinToString(), color = Plum, modifier = Modifier.padding(top = 6.dp))
        Text(
            "페이지 수 : ${book.pageCount?.let { "${it}쪽" } ?: "정보없음"}",
            color = Color(0xFF766B78),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text("독서 상태", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingStatus.entries.forEach { option ->
                FilterChip(selected = status == option, onClick = { status = option }, label = { Text(option.label) })
            }
        }
        if (status != ReadingStatus.WANT_TO_READ) {
            CalendarDateField("시작일", startedOn, { startedOn = it }, required = true, Modifier.padding(top = 10.dp))
        }
        if (status == ReadingStatus.READ) {
            CalendarDateField("종료일", finishedOn, { finishedOn = it }, required = true, Modifier.padding(top = 10.dp))
        }
        Text("별점 (선택)", color = Ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { star ->
                FilterChip(selected = rating == star.toDouble(), onClick = { rating = if (rating == star.toDouble()) null else star.toDouble() }, label = { Text("${star}점") })
            }
        }
        OutlinedTextField(review, { if (it.length <= 200) review = it }, label = { Text("한줄평 (선택, ${review.length}/200)") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 2)
        Row(Modifier.fillMaxWidth().clickable { favorite = !favorite }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = favorite, onCheckedChange = { favorite = it })
            Text("즐겨찾기", color = Ink)
        }
        Button(
            onClick = { vm.add(book, status, favorite, rating, review, startedOn, finishedOn) },
            enabled = !vm.loading && when (status) {
                ReadingStatus.WANT_TO_READ -> true
                ReadingStatus.READING -> startedOn.isNotBlank()
                ReadingStatus.READ -> startedOn.isNotBlank() && finishedOn.isNotBlank()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text(if (book.readingStatus == null) "내 책장에 등록" else "기록 저장") }
        OutlinedButton({ vm.goBack() }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("돌아가기") }
    }
}

@Composable private fun CalendarDateField(label: String, value: String, onValueChange: (String) -> Unit, required: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val initial = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, year, month, day ->
            onValueChange(LocalDate.of(year, month + 1, day).toString())
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }, modifier = modifier.fillMaxWidth()) {
        Text("$label${if (required) " *" else ""}  ${value.ifBlank { "날짜 선택" }}")
    }
}

@Composable private fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberSession by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("로그인", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("로그인하면 읽고 싶은 책과 독서 기록을 나만의 책장에 보관할 수 있어요.", color = Color(0xFF766B78), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("이메일") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("비밀번호") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().clickable { rememberSession = !rememberSession }.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = rememberSession, onCheckedChange = { rememberSession = it })
            Text("자동 로그인", color = Ink)
        }
        Button({ vm.login(email, password, rememberSession) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("로그인") }
        Button({ vm.openSignUp() }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("이메일로 회원가입") }
        Text("이메일과 비밀번호로 가입해 나만의 책장을 시작하세요.", color = Color(0xFF766B78), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable private fun SignUpScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    var ageGroup by remember { mutableStateOf<Int?>(null) }
    var ageMenuExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("회원가입", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("간단한 정보로 나만의 독서 책장을 시작하세요.", color = Color(0xFF766B78), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = email, onValueChange = { email = it; vm.emailAvailable = null; vm.emailAvailabilityMessage = null; vm.emailAvailabilityError = false }, label = { Text("이메일") }, singleLine = true, isError = vm.emailAvailabilityError, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button({ vm.checkEmailAvailability(email) }, enabled = !vm.loading, modifier = Modifier.height(56.dp)) { Text("중복 확인") }
        }
        vm.emailAvailabilityMessage?.let { message ->
            Text(message, color = if (vm.emailAvailabilityError) MaterialTheme.colorScheme.error else Plum, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("비밀번호 (6자 이상)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = passwordConfirmation, onValueChange = { passwordConfirmation = it }, label = { Text("비밀번호 확인") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("닉네임 (2~20자)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text("성별 (선택)", color = Ink, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("MALE" to "남성", "FEMALE" to "여성").forEach { (value, label) ->
                FilterChip(
                    selected = gender == value,
                    onClick = { gender = if (gender == value) null else value },
                    label = { Text(label) },
                    leadingIcon = if (gender == value) {{ Icon(Icons.Outlined.Check, contentDescription = "선택됨", Modifier.size(18.dp)) }} else null
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("연령대 (선택)", color = Ink, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            OutlinedButton(onClick = { ageMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(ageGroup?.let { "${it}대" } ?: "선택 안 함")
            }
            DropdownMenu(expanded = ageMenuExpanded, onDismissRequest = { ageMenuExpanded = false }) {
                DropdownMenuItem(text = { Text("선택 안 함") }, onClick = { ageGroup = null; ageMenuExpanded = false })
                (10..90 step 10).forEach { age ->
                    DropdownMenuItem(text = { Text("${age}대") }, onClick = { ageGroup = age; ageMenuExpanded = false })
                }
            }
        }
        Button(
            {
                val validationMessage = when {
                    vm.emailAvailable != true -> "이메일 중복체크를 해주세요."
                    password.isBlank() -> "비밀번호를 입력해주세요."
                    password.length < 6 -> "비밀번호는 6자 이상 입력해주세요."
                    passwordConfirmation.isBlank() -> "비밀번호 확인을 입력해주세요."
                    password != passwordConfirmation -> "비밀번호 확인이 틀렸습니다."
                    nickname.isBlank() -> "닉네임을 입력해주세요."
                    nickname.trim().length !in 2..20 -> "닉네임은 2~20자로 입력해주세요."
                    else -> null
                }
                if (validationMessage != null) Toast.makeText(context, validationMessage, Toast.LENGTH_SHORT).show()
                else vm.signUp(email, password, passwordConfirmation, nickname, gender, ageGroup)
            },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) { Text("회원가입") }
    }
}

@Composable private fun ShelfScreen(vm: MainViewModel) {
    var selectedStatus by remember { mutableStateOf<ReadingStatus?>(null) }
    var shelfView by remember { mutableStateOf(true) }
    val filtered = vm.shelf.filter { selectedStatus == null || it.readingStatus == selectedStatus }
    val readBooks = vm.shelf.filter { it.readingStatus == ReadingStatus.READ }
    val estimatedCentimeters = readBooks.sumOf { estimatedSpineMm(it) } / 10.0
    var draggingIsbn by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableStateOf(Offset.Zero) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragTargetIndex by remember { mutableStateOf(-1) }
    val density = LocalDensity.current
    val horizontalStep = with(density) { 30.dp.toPx() }
    val verticalStep = with(density) { 90.dp.toPx() }
    Column(Modifier.fillMaxSize()) {
        Text("나의 책장 ${vm.shelf.size}권", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("읽은 책", "${readBooks.size}권", Modifier.weight(1f))
            SummaryCard("예상 책등", "%.1fcm".format(estimatedCentimeters), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selectedStatus == null, { selectedStatus = null }, label = { Text("전체") })
            ReadingStatus.entries.forEach { status -> FilterChip(selectedStatus == status, { selectedStatus = status }, label = { Text(status.label) }) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shelfView) Button({ shelfView = true }, Modifier.weight(1f)) { Text("책장 보기") }
            else OutlinedButton({ shelfView = true }, Modifier.weight(1f)) { Text("책장 보기") }
            if (!shelfView) Button({ shelfView = false }, Modifier.weight(1f)) { Text("표지 보기") }
            else OutlinedButton({ shelfView = false }, Modifier.weight(1f)) { Text("표지 보기") }
        }
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("이 책장에는 아직 책이 없어요.", color = Color(0xFF766B78))
                Button({ vm.openTopLevel("search") }, Modifier.padding(top = 12.dp)) { Text("첫 책 등록하기") }
            }
        } else if (shelfView) {
            Text(
                if (selectedStatus == null) "책을 길게 누른 뒤 움직여 순서를 바꿀 수 있어요."
                else "순서 변경은 전체 필터에서 사용할 수 있어요.",
                color = Color(0xFF766B78),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                itemsIndexed(filtered.chunked(8), key = { rowIndex, _ -> rowIndex }) { rowIndex, shelfRow ->
                    BookshelfTier(
                        books = shelfRow,
                        firstIndex = rowIndex * 8,
                        onBookClick = vm::openShelfDetail,
                        reorderEnabled = selectedStatus == null && !vm.reorderingShelf,
                        draggingIsbn = draggingIsbn,
                        dragOffset = dragDistance,
                        onDragStart = { book ->
                            draggingIsbn = book.isbn13
                            dragDistance = Offset.Zero
                            dragStartIndex = vm.shelf.indexOfFirst { it.isbn13 == book.isbn13 }
                            dragTargetIndex = dragStartIndex
                            vm.beginShelfReorder()
                        },
                        onDrag = { _, amount ->
                            dragDistance += amount
                            if (dragStartIndex >= 0) {
                                val horizontalSlots = (dragDistance.x / horizontalStep).roundToInt()
                                val verticalRows = (dragDistance.y / verticalStep).roundToInt()
                                dragTargetIndex = (dragStartIndex + horizontalSlots + verticalRows * 8)
                                    .coerceIn(0, vm.shelf.lastIndex)
                            }
                        },
                        onDragEnd = {
                            val movedBook = vm.shelf.firstOrNull { it.isbn13 == draggingIsbn }
                            if (movedBook != null && dragTargetIndex >= 0) vm.moveShelfBook(movedBook, dragTargetIndex)
                            draggingIsbn = null
                            dragDistance = Offset.Zero
                            dragStartIndex = -1
                            dragTargetIndex = -1
                            vm.finishShelfReorder()
                        },
                        onDragCancel = {
                            draggingIsbn = null
                            dragDistance = Offset.Zero
                            dragStartIndex = -1
                            dragTargetIndex = -1
                            vm.cancelShelfReorder()
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                gridItems(filtered, key = { it.isbn13 ?: it.title }) { book -> CoverBookCard(book) { vm.openShelfDetail(book) } }
            }
        }
    }
}

@Composable private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SoftPlum), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp)) { Text(value, color = Ink, fontWeight = FontWeight.Bold); Text(label, color = Plum, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable private fun CoverBookCard(book: BookSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SoftPlum)) {
            BookCover(book, Modifier.fillMaxWidth().aspectRatio(0.7f), ContentScale.Crop)
        }
        Text(book.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        Text(book.readingStatus?.label.orEmpty(), color = Plum, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun RecordsScreen(vm: MainViewModel) {
    var showStatistics by remember { mutableStateOf(false) }
    val reviewed = vm.shelf.filter { !it.reviewText.isNullOrBlank() }.sortedByDescending { it.finishedOn ?: it.startedOn.orEmpty() }
    val currentYear = LocalDate.now().year
    val monthly = (1..12).associateWith { month ->
        vm.shelf.filter { book ->
            book.readingStatus == ReadingStatus.READ && runCatching {
                val date = LocalDate.parse(book.finishedOn ?: return@runCatching false)
                date.year == currentYear && date.monthValue == month
            }.getOrDefault(false)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text("독서 기록", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!showStatistics) Button({ showStatistics = false }, Modifier.weight(1f)) { Text("한줄평") }
            else OutlinedButton({ showStatistics = false }, Modifier.weight(1f)) { Text("한줄평") }
            if (showStatistics) Button({ showStatistics = true }, Modifier.weight(1f)) { Text("통계") }
            else OutlinedButton({ showStatistics = true }, Modifier.weight(1f)) { Text("통계") }
        }
        if (!showStatistics) {
            if (reviewed.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 작성한 한줄평이 없어요.", color = Color(0xFF766B78))
                    Text("책장에서 책을 선택해 첫 기록을 남겨보세요.", color = Plum, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            } else LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                items(reviewed, key = { it.isbn13 ?: it.title }) { book ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { vm.openShelfDetail(book) }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            BookCover(book, Modifier.width(58.dp).height(82.dp), ContentScale.Crop)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(book.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                book.rating?.let { Text("${it.toInt()}점", color = Plum, style = MaterialTheme.typography.labelMedium) }
                                Text(book.reviewText.orEmpty(), color = Color(0xFF5F5661), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                Text(book.finishedOn ?: book.startedOn.orEmpty(), color = Color(0xFF827984), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        } else {
            val maxBooks = monthly.values.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            val maxPages = monthly.values.maxOfOrNull { books -> books.sumOf { it.pageCount ?: 0 } }?.coerceAtLeast(1) ?: 1
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 14.dp)) {
                Text("${currentYear}년 월별 독서", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("종료일을 기록한 읽은 책을 기준으로 집계합니다.", color = Color(0xFF766B78), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                MonthlyBarChart("권수별", monthly.mapValues { it.value.size }, maxBooks, "권")
                MonthlyBarChart("페이지별", monthly.mapValues { entry -> entry.value.sumOf { it.pageCount ?: 0 } }, maxPages, "쪽")
                if (monthly.values.all { it.isEmpty() }) Text("종료일이 기록된 완독 도서가 아직 없어요.", color = Color(0xFF766B78), modifier = Modifier.padding(top = 28.dp))
            }
        }
    }
}

@Composable private fun MonthlyBarChart(title: String, values: Map<Int, Int>, maximum: Int, unit: String) {
    Text(title, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().height(190.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            values.forEach { (month, value) ->
                Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (value > 0) Text("$value$unit", color = Plum, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Box(Modifier.width(22.dp).height((130f * value / maximum).coerceAtLeast(if (value > 0) 6f else 0f).dp).background(if (value > 0) Plum else SoftPlum, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                    Text("${month}월", color = Color(0xFF766B78), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable private fun ProfileScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text("마이", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (vm.accessToken == null) {
            Text("로그인하고 나만의 독서 기록을 안전하게 보관하세요.", color = Color(0xFF766B78))
            Button(
                onClick = { vm.openLogin("profile") },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) { Text("로그인") }
            Button(
                onClick = { vm.previousScreen = "profile"; vm.openSignUp() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("회원가입") }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SoftPlum),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("로그인됨", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("등록한 책과 독서 상태가 계정에 저장되고 있어요.", color = Plum, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Button(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) { Text("로그아웃") }
        }
    }
}

private fun spineWidthDp(book: BookSummary): Double {
    book.pageCount?.takeIf { it > 0 }?.let { pages ->
        val minimumPages = 50.0
        val maximumPages = 1_200.0
        val normalized = ((ln(pages.toDouble().coerceIn(minimumPages, maximumPages)) - ln(minimumPages)) /
            (ln(maximumPages) - ln(minimumPages))).coerceIn(0.0, 1.0)
        return 18.0 + (normalized * 30.0)
    }
    return book.thicknessMm?.times(1.25)?.coerceIn(18.0, 48.0) ?: 28.0
}

private fun estimatedSpineMm(book: BookSummary): Double = when {
    book.pageCount != null -> 1.5 + (book.pageCount * 0.055)
    book.thicknessMm != null -> book.thicknessMm
    else -> 14.0
}

@Composable
private fun BookshelfTier(
    books: List<BookSummary>,
    firstIndex: Int,
    onBookClick: (BookSummary) -> Unit,
    reorderEnabled: Boolean,
    draggingIsbn: String?,
    dragOffset: Offset,
    onDragStart: (BookSummary) -> Unit,
    onDrag: (BookSummary, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().height(286.dp).clip(RoundedCornerShape(16.dp)).background(
            Brush.verticalGradient(listOf(Color(0xFF4A302A), Color(0xFF6D4A3B), Color(0xFF3B2623)))
        ).border(1.dp, Color(0xFF8E6853), RoundedCornerShape(16.dp))
    ) {
        Box(
            Modifier.fillMaxWidth().height(232.dp).padding(horizontal = 12.dp).align(Alignment.TopCenter)
                .background(Color(0xFF2D211F).copy(alpha = 0.32f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
        )
        Row(
            Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 14.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom
        ) {
            books.forEachIndexed { index, book ->
                val isDragging = draggingIsbn == book.isbn13
                val dragModifier = if (reorderEnabled) Modifier.pointerInput(book.isbn13) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart(book) },
                        onDrag = { change, amount -> change.consume(); onDrag(book, amount) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel
                    )
                } else Modifier
                Spine(
                    book,
                    firstIndex + index,
                    Modifier.clickable(enabled = !isDragging) { onBookClick(book) }
                        .then(dragModifier)
                        .graphicsLayer {
                            scaleX = if (isDragging) 1.08f else 1f
                            scaleY = if (isDragging) 1.08f else 1f
                            shadowElevation = if (isDragging) 16.dp.toPx() else 0f
                            alpha = if (isDragging) 0.88f else 1f
                            translationX = if (isDragging) dragOffset.x else 0f
                            translationY = if (isDragging) dragOffset.y else 0f
                        }
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Box(
            Modifier.fillMaxWidth().height(26.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color(0xFF9A7157), Color(0xFF6A4638), Color(0xFF3C2824)))
            ).border(1.dp, Color(0xFFB68B69).copy(alpha = 0.55f))
        )
        Box(Modifier.fillMaxWidth().height(5.dp).align(Alignment.BottomCenter).background(Color(0xFF231716).copy(alpha = 0.45f)))
    }
}

@Composable
private fun Spine(book: BookSummary, index: Int, modifier: Modifier = Modifier) {
    val width = spineWidthDp(book).dp
    val height = (202 + ((book.isbn13 ?: book.title).hashCode().ushr(1) % 30)).dp
    val palette = listOf(Color(0xFF36586B), Color(0xFF7D4050), Color(0xFF9A6545), Color(0xFF4D506F), Color(0xFF496451))
    val fallback = palette[index % palette.size]
    val shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

    Box(
        modifier.width(width).height(height).padding(end = 3.dp).clip(shape).background(fallback)
            .border(1.dp, Color.Black.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = book.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize().background(fallback)) },
            error = { Box(Modifier.fillMaxSize().background(fallback)) },
            success = { SubcomposeAsyncImageContent() }
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.42f),
                        Color.Black.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.10f),
                        Color.Black.copy(alpha = 0.34f)
                    )
                )
            )
        )
        Box(Modifier.fillMaxWidth().height(7.dp).align(Alignment.TopCenter).background(Color.White.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth().height(8.dp).align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.28f)))
        Text(
            text = book.title,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.requiredWidth(165.dp).rotate(90f).background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
        )
        book.readingStatus?.let {
            Box(Modifier.width(6.dp).height(30.dp).align(Alignment.TopEnd).background(Plum))
        }
    }
}
