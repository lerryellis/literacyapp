package ayola.literacyapp.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ayola.literacyapp.game.ui.navigation.AppNavigation
import ayola.literacyapp.game.ui.theme.LiteracyAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiteracyAppTheme {
                AppNavigation()
            }
        }
    }
}
