package org.sableos.games

import org.sableos.design.SableTexts

/**
 * Every line Sable Games shows, in English, by string resource name. res/values/strings.xml
 * repeats them (GamesTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object GamesText {
    val EN: Map<String, String> =
        mapOf(
            "game_over_space_plays_again" to "Game over · Space plays again",
            "paused_p_resumes" to "Paused · P resumes",
            "snake" to "Snake",
            "eat_grow_and_keep_clear" to "Eat, grow and keep clear of the walls and your tail.",
            "score_best" to "score · best %1\$s",
            "arrows_or_the_trackpad_turn" to
                "Arrows or the trackpad turn, swipes too; P pauses, N starts over.",
            "resume" to "Resume",
            "pause" to "Pause",
            "new_label" to "New",
            "blocks" to "Blocks",
            "fit_the_falling_pieces_into" to "Fit the falling pieces into full rows.",
            "score_level" to "score · level %1\$s",
            "rows" to "%1\$s · %2\$s rows",
            "left_right_move_up_turns" to
                "Left/Right move, Up turns, Down drops a row, Space drops it; tap turns, swipes " +
                "move. P pauses.",
            "bricks" to "Bricks",
            "keep_the_ball_in_play" to "Keep the ball in play and break every brick.",
            "score_lives_level" to "score · lives %1\$s · level %2\$s",
            "space_launches" to "%1\$s · Space launches",
            "left_right_or_the_trackpad" to
                "Left/Right or the trackpad move the paddle, or drag it; Space or a tap " +
                "launches. P pauses.",
            "reversi" to "Reversi",
            "outflank_the_phone_s_discs" to "Outflank the phone's discs to turn them yours.",
            "you_phone" to "you %1\$s · phone %2\$s",
            "you_win" to "You win",
            "the_phone_wins" to "The phone wins",
            "a_draw" to "A draw",
            "your_move" to "Your move",
            "the_phone_is_thinking" to "The phone is thinking",
            "arrows_move_enter_or_space" to
                "Arrows move, Enter or Space places a disc; dots show where you can play. N " +
                "starts over.",
            "can_t_go_there" to "Can't go there",
            "solitaire" to "Solitaire",
            "build_each_suit_from_ace" to "Build each suit from ace to king.",
            "you_won_in_moves" to "You won in %1\$s moves",
            "pick_where_it_goes" to "%1\$s · pick where it goes",
            "arrows_move_enter_picks_up" to
                "Arrows move, Enter picks up and puts down, Space turns a card, A sends it home, " +
                "Del drops it. N deals again.",
            "turn" to "Turn",
            "well_done" to "Well done",
            "mahjong" to "Mahjong",
            "clear_the_pile_two_matching" to "Clear the pile two matching free tiles at a time.",
            "tiles_left" to "tiles left",
            "cleared" to "Cleared!",
            "no_pairs_left_n_deals" to "%1\$s · no pairs left, N deals again",
            "a_tile_is_free_when" to
                "A tile is free when nothing is on it and its left or right side is open. Arrows " +
                "move, Enter picks, H hints, N deals.",
            "hint" to "Hint",
            "words" to "Words",
            "find_the_five_letter_word" to "Find the five-letter word in six guesses.",
            "guess_of" to "guess %1\$s of %2\$s",
            "got_it_enter_plays_again" to "Got it! Enter plays again",
            "it_was_enter_plays_again" to "It was %1\$s · Enter plays again",
            "not_in_it" to "Not in it: %1\$s",
            "type_a_word_enter_guesses" to "Type a word, Enter guesses",
            "green_right_place_amber_in" to
                "Green: right place. Amber: in the word, elsewhere. Type letters, Del erases, " +
                "Enter guesses.",
            "slide_merge_and_build_the" to "Slide, merge and build the highest tile.",
            "swipe_the_board_or_use" to
                "Swipe the board or use the arrows (trackpad) to move every tile; N starts over.",
            "swipe_left_right_up_or" to
                "Swipe left, right, up or down. Equal tiles merge into the next value.",
            "clear_the_field" to "Clear the field",
            "new_field" to "New field",
            "clear_the_field_without_touching" to "Clear the field without touching a mine.",
            "flag_mode" to "flag mode",
            "reveal_mode" to "reveal mode",
            "n9_9_field_10_mines" to
                "9 × 9 field · 10 mines · arrows move, Space reveals, F flags, N new field",
            "reveal" to "Reveal",
            "flag" to "Flag",
            "choose_a_cell" to "Choose a cell",
            "new_puzzle" to "New puzzle",
            "solved" to "Solved",
            "cell_erased" to "Cell erased",
            "move_accepted" to "Move accepted",
            "nothing_to_undo" to "Nothing to undo",
            "last_move_undone" to "Last move undone",
            "fixed_clue" to "Fixed clue",
            "row_column" to "Row %1\$s, column %2\$s",
            "a_focused_number_puzzle" to "A focused number puzzle.",
            "arrows_move_1_9_or" to
                "Arrows move, 1-9 (or W E R S D F Z X C) fill, Del erases, U undoes, N starts " +
                "over.",
            "undo" to "Undo",
            "erase" to "Erase",
        )

    val t = SableTexts(EN)
}
